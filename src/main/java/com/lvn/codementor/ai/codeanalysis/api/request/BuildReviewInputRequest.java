package com.lvn.codementor.ai.codeanalysis.api.request;

/**
 * Body for building a review input. Currently empty — the snapshot is identified by the request path
 * and all limits/policies are server-controlled. Reserved for future, explicitly-safe options.
 */
public record BuildReviewInputRequest() {
}
