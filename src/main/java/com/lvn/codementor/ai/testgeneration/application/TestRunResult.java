package com.lvn.codementor.ai.testgeneration.application;

import com.lvn.codementor.ai.testgeneration.domain.TestResult;
import com.lvn.codementor.ai.testgeneration.domain.TestRun;

public record TestRunResult(TestRun run, TestResult result) {
}
