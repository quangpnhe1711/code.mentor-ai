package com.lvn.codementor.ai.repository.api.response;

import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;

/** A snapshot file-inventory row as returned by the API. Metadata only — no file content. */
public record RepositoryFileEntryResponse(
        String path, String language, long sizeBytes, boolean included, String skipReason) {

    public static RepositoryFileEntryResponse from(RepositoryFileEntry e) {
        return new RepositoryFileEntryResponse(
                e.getPath(), e.getLanguage(), e.getSizeBytes(), e.isIncluded(), e.getSkipReason());
    }
}
