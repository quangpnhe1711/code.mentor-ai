package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import com.lvn.codementor.ai.review.config.ReviewAiProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codementor.review.ai.provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalReviewAnalyzerClient implements ReviewAnalyzerClient {

    private final LocalDeterministicReviewAnalyzer analyzer;
    private final ReviewAiProperties properties;

    public LocalReviewAnalyzerClient(LocalDeterministicReviewAnalyzer analyzer, ReviewAiProperties properties) {
        this.analyzer = analyzer;
        this.properties = properties;
    }

    @Override
    public ReviewAnalyzerResult analyze(List<CodeAnalysisFile> files) {
        return new ReviewAnalyzerResult(
                properties.provider(),
                properties.model(),
                properties.promptVersion(),
                analyzer.analyze(files));
    }
}
