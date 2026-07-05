package io.github.nianliu.archimedes.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiModelTest {

    @Test
    void apiInfoHoldsValues() {
        ParamInfo id = new ParamInfo("id", ParamSource.PATH, "java.lang.Long", true);

        ApiInfo api = new ApiInfo();
        api.setControllerClass("com.example.UserController");
        api.setHandlerMethod("getUser");
        api.setHttpMethods(List.of("GET"));
        api.setPaths(List.of("/api/users/{id}"));
        api.setParams(List.of(id));
        api.setReturnType("com.example.User");
        api.setDeprecated(true);

        assertThat(api.getControllerClass()).isEqualTo("com.example.UserController");
        assertThat(api.getPaths()).containsExactly("/api/users/{id}");
        assertThat(api.isDeprecated()).isTrue();
        assertThat(api.getParams()).hasSize(1);
        assertThat(api.getParams().get(0).getSource()).isEqualTo(ParamSource.PATH);
        assertThat(api.getParams().get(0).getName()).isEqualTo("id");
        assertThat(api.getParams().get(0).isRequired()).isTrue();
    }
}
