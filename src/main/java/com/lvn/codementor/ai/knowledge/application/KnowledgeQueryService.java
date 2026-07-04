package com.lvn.codementor.ai.knowledge.application;

import com.lvn.codementor.ai.codeanalysis.application.ReviewInputMaterializer;
import com.lvn.codementor.ai.codeanalysis.application.result.MaterializedReviewInput;
import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.knowledge.domain.ChatMessage;
import com.lvn.codementor.ai.knowledge.domain.ChatMessageRole;
import com.lvn.codementor.ai.knowledge.domain.ChatSession;
import com.lvn.codementor.ai.knowledge.domain.Citation;
import com.lvn.codementor.ai.knowledge.persistence.ChatMessageJpaRepository;
import com.lvn.codementor.ai.knowledge.persistence.ChatSessionJpaRepository;
import com.lvn.codementor.ai.knowledge.persistence.CitationJpaRepository;
import com.lvn.codementor.ai.organization.application.OrganizationAccessService;
import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshotStatus;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositoryFileEntryJpaRepository;
import com.lvn.codementor.ai.repository.persistence.RepositorySnapshotJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local Knowledge foundation for FR-011/FR-012. It produces citation-backed answers from the latest
 * READY snapshot without calling an AI provider or persisting source snippets.
 */
@Service
public class KnowledgeQueryService {

    private static final int MAX_CITATIONS = 5;

    private final OrganizationAccessService access;
    private final ImportedRepositoryJpaRepository repositories;
    private final RepositorySnapshotJpaRepository snapshots;
    private final RepositoryFileEntryJpaRepository fileEntries;
    private final ReviewInputMaterializer materializer;
    private final ChatSessionJpaRepository sessions;
    private final ChatMessageJpaRepository messages;
    private final CitationJpaRepository citations;

    public KnowledgeQueryService(
            OrganizationAccessService access,
            ImportedRepositoryJpaRepository repositories,
            RepositorySnapshotJpaRepository snapshots,
            RepositoryFileEntryJpaRepository fileEntries,
            ReviewInputMaterializer materializer,
            ChatSessionJpaRepository sessions,
            ChatMessageJpaRepository messages,
            CitationJpaRepository citations) {
        this.access = access;
        this.repositories = repositories;
        this.snapshots = snapshots;
        this.fileEntries = fileEntries;
        this.materializer = materializer;
        this.sessions = sessions;
        this.messages = messages;
        this.citations = citations;
    }

    @Transactional
    public KnowledgeAnswer ask(UUID userId, UUID organizationId, UUID repositoryId, String question) {
        access.requireMember(userId, organizationId);
        repositories.findByIdAndOrganizationId(repositoryId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Repository not found"));

        String normalizedQuestion = normalizeQuestion(question);
        RepositorySnapshot snapshot = snapshots
                .findFirstByOrganizationIdAndRepositoryIdAndStatusOrderByCreatedAtDesc(
                        organizationId, repositoryId, RepositorySnapshotStatus.READY)
                .orElseThrow(() -> new AppException(
                        ErrorCode.SNAPSHOT_NOT_READY, "A READY repository snapshot is required before asking questions"));

        ChatSession session = sessions.save(new ChatSession(
                organizationId, repositoryId, snapshot.getId(), userId, titleFrom(normalizedQuestion)));
        messages.save(new ChatMessage(session.getId(), ChatMessageRole.USER, normalizedQuestion));

        List<CitationDraft> drafts = findCitations(snapshot, normalizedQuestion);
        String answer = answerFrom(drafts);
        ChatMessage assistant = messages.save(new ChatMessage(session.getId(), ChatMessageRole.ASSISTANT, answer));
        List<Citation> savedCitations = citations.saveAll(drafts.stream()
                .map(draft -> draft.toCitation(assistant.getId()))
                .toList());

        return new KnowledgeAnswer(session, answer, savedCitations);
    }

    private List<CitationDraft> findCitations(RepositorySnapshot snapshot, String question) {
        List<CitationDraft> fromContent = findContentCitations(snapshot, question);
        if (!fromContent.isEmpty()) {
            return fromContent;
        }
        return findInventoryCitations(snapshot.getId(), question);
    }

    private List<CitationDraft> findContentCitations(RepositorySnapshot snapshot, String question) {
        try {
            MaterializedReviewInput input = materializer.materialize(snapshot.getId());
            List<String> terms = queryTerms(question);
            List<CitationDraft> matches = new ArrayList<>();
            for (CodeAnalysisFile file : input.files()) {
                String[] lines = file.sanitizedContent().split("\\R", -1);
                for (int i = 0; i < lines.length; i++) {
                    int score = score(file.path(), lines[i], terms);
                    if (score > 0) {
                        matches.add(new CitationDraft(file.path(), i + 1, i + 1, "Matched repository content", score));
                    }
                }
            }
            Map<String, CitationDraft> bestByFile = new LinkedHashMap<>();
            for (CitationDraft match : matches) {
                bestByFile.merge(match.filePath(), match, KnowledgeQueryService::betterCitation);
            }
            return bestByFile.values().stream()
                    .sorted(Comparator.comparing(CitationDraft::filePath))
                    .limit(MAX_CITATIONS)
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<CitationDraft> findInventoryCitations(UUID snapshotId, String question) {
        List<String> terms = queryTerms(question);
        return fileEntries.findBySnapshotIdAndIncluded(snapshotId, true).stream()
                .filter(entry -> score(entry.getPath(), entry.getLanguage(), terms) > 0)
                .sorted(Comparator.comparing(RepositoryFileEntry::getPath))
                .limit(MAX_CITATIONS)
                .map(entry -> new CitationDraft(entry.getPath(), null, null, "Matched repository file inventory", 1))
                .toList();
    }

    private static CitationDraft betterCitation(CitationDraft left, CitationDraft right) {
        if (right.score() > left.score()) {
            return right;
        }
        if (right.score() == left.score() && right.lineStart() != null && left.lineStart() != null
                && right.lineStart() < left.lineStart()) {
            return right;
        }
        return left;
    }

    private static int score(String path, String content, List<String> terms) {
        String haystack = ((path == null ? "" : path) + " " + (content == null ? "" : content))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private static List<String> queryTerms(String question) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : question.toLowerCase(Locale.ROOT).split("[^a-z0-9_./-]+")) {
            if (token.length() >= 3 && !isStopWord(token)) {
                terms.add(token);
            }
        }
        return terms.isEmpty() ? List.of(question.toLowerCase(Locale.ROOT)) : List.copyOf(terms);
    }

    private static boolean isStopWord(String token) {
        return Set.of("the", "and", "for", "with", "where", "which", "what", "how", "does", "this", "that", "any")
                .contains(token);
    }

    private static String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "question is required");
        }
        String trimmed = question.trim();
        if (trimmed.length() > 1000) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "question must be 1000 characters or fewer");
        }
        return trimmed;
    }

    private static String titleFrom(String question) {
        return question.length() <= 80 ? question : question.substring(0, 77) + "...";
    }

    private static String answerFrom(List<CitationDraft> citations) {
        if (citations.isEmpty()) {
            return "I could not find a matching file in the latest READY snapshot.";
        }
        return "I found " + citations.size() + " relevant source location(s) in the latest READY snapshot.";
    }

    private record CitationDraft(String filePath, Integer lineStart, Integer lineEnd, String reason, int score) {
        Citation toCitation(UUID chatMessageId) {
            return new Citation(chatMessageId, filePath, lineStart, lineEnd, reason);
        }
    }
}
