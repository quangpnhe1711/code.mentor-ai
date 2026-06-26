package com.lvn.codementor.ai;

import com.lvn.codementor.ai.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;

/** Verifies the Spring context loads (which requires the Flyway migration to apply successfully). */
class ApplicationTests extends AbstractPostgresIT {

    @Test
    void contextLoads() {
    }
}
