package com.lvn.codementor.ai.review.application.command;

/** Optional filters for listing structured review findings. */
public record ReviewFindingFilter(String severity, String category, String filePath) {
}
