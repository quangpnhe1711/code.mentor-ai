package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.review.application.LocalDeterministicReviewAnalyzer.Finding;
import java.util.List;

public record ReviewAnalyzerResult(
        String aiProvider,
        String aiModel,
        String promptVersion,
        List<Finding> findings) {

    public ReviewAnalyzerResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
