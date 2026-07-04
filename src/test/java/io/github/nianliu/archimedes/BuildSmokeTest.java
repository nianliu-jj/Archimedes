package io.github.nianliu.archimedes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSmokeTest {

    @Test
    void toolchainIsWired() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(17);
    }
}
