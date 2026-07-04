package com.lvn.codementor.ai.testgeneration.api.response;

import com.lvn.codementor.ai.testgeneration.application.TestGenerationResult;
import java.util.List;

public record TestGenerationResultResponse(
        TestGenerationJobResponse job,
        List<GeneratedTestResponse> generatedTests) {

    public static TestGenerationResultResponse from(TestGenerationResult result) {
        return new TestGenerationResultResponse(
                TestGenerationJobResponse.from(result.job()),
                result.generatedTests().stream().map(GeneratedTestResponse::from).toList());
    }
}
