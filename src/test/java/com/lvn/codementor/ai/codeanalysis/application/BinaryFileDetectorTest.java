package com.lvn.codementor.ai.codeanalysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BinaryFileDetectorTest {

    private final BinaryFileDetector detector = new BinaryFileDetector();

    @Test
    void detectsNulByteAsBinary() {
        byte[] content = {'{', 0x00, '}'};
        assertThat(detector.isBinary(content)).isTrue();
    }

    @Test
    void treatsUtf8TextAsNonBinary() {
        byte[] content = "class App {\n  // café ☕\n}\n".getBytes(StandardCharsets.UTF_8);
        assertThat(detector.isBinary(content)).isFalse();
    }

    @Test
    void treatsEmptyContentAsNonBinary() {
        assertThat(detector.isBinary(new byte[0])).isFalse();
    }
}
