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

    @Test
    void rpcAndWsCarryDescription() {
        RpcApiInfo svc = new RpcApiInfo(RpcApiInfo.PROTOCOL_DUBBO, "com.demo.S", null, null, null);
        svc.setDescription("定价服务");
        assertThat(svc.getDescription()).isEqualTo("定价服务");

        RpcMethodInfo m = new RpcMethodInfo("price", null, "java.math.BigDecimal");
        m.setDescription("计算价格");
        assertThat(m.getDescription()).isEqualTo("计算价格");

        WsApiInfo ws = new WsApiInfo(WsApiInfo.KIND_HANDLER, "/ws/echo", "EchoHandler", null, false);
        ws.setDescription("回声端点");
        assertThat(ws.getDescription()).isEqualTo("回声端点");
    }

    @org.junit.jupiter.api.Test
    void apiInfoResponsesDefaultEmptyAndSettable() {
        ApiInfo info = new ApiInfo();
        org.assertj.core.api.Assertions.assertThat(info.getResponses()).isEmpty();

        ResponseInfo r = new ResponseInfo(404, "订单不存在", null, null);
        info.setResponses(java.util.List.of(r));
        org.assertj.core.api.Assertions.assertThat(info.getResponses()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(info.getResponses().get(0).getCode()).isEqualTo(404);
        org.assertj.core.api.Assertions.assertThat(info.getResponses().get(0).getDescription()).isEqualTo("订单不存在");

        info.setResponses(null); // null 归一为空列表
        org.assertj.core.api.Assertions.assertThat(info.getResponses()).isEmpty();
    }
}
