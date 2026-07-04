package com.lvn.codementor.ai.testgeneration.application;

import com.lvn.codementor.ai.testgeneration.domain.GeneratedTest;
import com.lvn.codementor.ai.testgeneration.domain.TestGenerationJob;
import java.util.List;

public record TestGenerationResult(TestGenerationJob job, List<GeneratedTest> generatedTests) {
}
