package com.lvn.codementor.ai.review.application;

public record ReviewPublicationResult(boolean published, String eventMessage) {

    public static ReviewPublicationResult publishedComment() {
        return new ReviewPublicationResult(true, "Pull request review comment published.");
    }

    public static ReviewPublicationResult skipped(String reason) {
        return new ReviewPublicationResult(false, reason);
    }
}
