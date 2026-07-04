package com.lvn.codementor.ai.testgeneration.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "test_results")
public class TestResult extends BaseEntity {

    @Column(name = "test_run_id", nullable = false)
    private UUID testRunId;

    @Column(name = "stdout", nullable = false)
    private String stdout;

    @Column(name = "stderr", nullable = false)
    private String stderr;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "failure_explanation")
    private String failureExplanation;

    protected TestResult() {
        // for JPA
    }

    public TestResult(UUID testRunId, String stdout, String stderr, Integer exitCode, long durationMs, String failureExplanation) {
        this.testRunId = testRunId;
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
        this.failureExplanation = failureExplanation;
    }

    public UUID getTestRunId() {
        return testRunId;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getFailureExplanation() {
        return failureExplanation;
    }
}
