package com.lvn.codementor.ai.codeanalysis.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Reads a safe text file from the snapshot workspace. Raw bytes are read once and decoded as UTF-8;
 * line endings are normalized to {@code \n} so the review-input hash is stable across platforms.
 * File content is only ever held in memory — never logged or persisted.
 */
@Component
public class TextFileReader {

    /** Read all bytes of a file. Wraps {@link IOException} so callers can treat a read failure uniformly. */
    public byte[] readAllBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read snapshot file", e);
        }
    }

    /** Decode UTF-8 bytes to text with normalized ({@code \n}) line endings. */
    public String decode(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
