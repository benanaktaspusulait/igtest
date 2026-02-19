package com.ig.sre.resilience.core.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextTest {

    @Test
    void rejectsBlankDependencyKey() {
        assertThatThrownBy(() -> new RequestContext(" ", "lineStatus", "ip-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependencyKey");
    }

    @Test
    void rejectsBlankOperationName() {
        assertThatThrownBy(() -> new RequestContext("tfl", "", "ip-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operationName");
    }

    @Test
    void returnsAnonymousWhenClientKeyIsMissing() {
        assertThat(new RequestContext("tfl", "lineStatus", null).clientKeyOrAnonymous()).isEqualTo("anonymous");
        assertThat(new RequestContext("tfl", "lineStatus", " ").clientKeyOrAnonymous()).isEqualTo("anonymous");
    }

    @Test
    void returnsClientKeyWhenProvided() {
        assertThat(new RequestContext("tfl", "lineStatus", "ip-1").clientKeyOrAnonymous()).isEqualTo("ip-1");
    }
}
