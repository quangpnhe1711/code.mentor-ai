package com.lvn.codementor.ai.codeanalysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewInputHashServiceTest {

    private final ReviewInputHashService hashService = new ReviewInputHashService();

    @Test
    void stableForSameSanitizedInputRegardlessOfOrder() {
        List<CodeAnalysisFile> a = List.of(
                new CodeAnalysisFile("src/A.java", "class A {}", 0),
                new CodeAnalysisFile("src/B.java", "class B {}", 1));
        List<CodeAnalysisFile> reordered = List.of(
                new CodeAnalysisFile("src/B.java", "class B {}", 0),
                new CodeAnalysisFile("src/A.java", "class A {}", 0));

        assertThat(hashService.hash(a)).isEqualTo(hashService.hash(reordered));
    }

    @Test
    void differsWhenSanitizedContentDiffers() {
        String h1 = hashService.hash(List.of(new CodeAnalysisFile("src/A.java", "class A {}", 0)));
        String h2 = hashService.hash(List.of(new CodeAnalysisFile("src/A.java", "class A2 {}", 0)));

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void differsWhenPathDiffers() {
        String h1 = hashService.hash(List.of(new CodeAnalysisFile("src/A.java", "class A {}", 0)));
        String h2 = hashService.hash(List.of(new CodeAnalysisFile("src/renamed.java", "class A {}", 0)));

        assertThat(h1).isNotEqualTo(h2);
    }
}
