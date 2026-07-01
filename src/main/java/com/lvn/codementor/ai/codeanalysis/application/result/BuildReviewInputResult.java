package com.lvn.codementor.ai.codeanalysis.application.result;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisInput;

/**
 * Outcome of a build attempt: the persisted {@link CodeAnalysisInput} aggregate (metadata only). The
 * sanitized content and per-file text never appear here — they exist only transiently inside the
 * builder.
 */
public record BuildReviewInputResult(CodeAnalysisInput input) {
}
