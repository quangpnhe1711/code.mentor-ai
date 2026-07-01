package com.lvn.codementor.ai.codeanalysis.application;

import org.springframework.stereotype.Component;

/**
 * Heuristically decides whether a file's bytes are binary (and therefore unsuitable for a text-based
 * review input). Content is inspected in-memory only and never logged or stored.
 *
 * <p>Rules (applied to a bounded prefix): a NUL byte is a strong binary signal; otherwise, a high
 * proportion of non-text control bytes marks the content as binary.
 */
@Component
public class BinaryFileDetector {

    private static final int SAMPLE_BYTES = 8192;
    private static final double CONTROL_BYTE_THRESHOLD = 0.30;

    public boolean isBinary(byte[] content) {
        if (content.length == 0) {
            return false;
        }
        int limit = Math.min(content.length, SAMPLE_BYTES);
        int controlCount = 0;
        for (int i = 0; i < limit; i++) {
            int b = content[i] & 0xFF;
            if (b == 0x00) {
                return true; // NUL byte: definitively binary
            }
            boolean textual = b == 0x09 // tab
                    || b == 0x0A // LF
                    || b == 0x0D // CR
                    || b == 0x0C // form feed
                    || (b >= 0x20 && b != 0x7F); // printable (incl. high/UTF-8 continuation bytes)
            if (!textual) {
                controlCount++;
            }
        }
        return (double) controlCount / limit > CONTROL_BYTE_THRESHOLD;
    }
}
