package com.lvn.codementor.ai.knowledge.application;

import com.lvn.codementor.ai.knowledge.domain.ChatSession;
import com.lvn.codementor.ai.knowledge.domain.Citation;
import java.util.List;

public record KnowledgeAnswer(ChatSession session, String answer, List<Citation> citations) {
}
