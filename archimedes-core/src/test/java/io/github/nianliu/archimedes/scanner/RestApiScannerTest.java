package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.FieldInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.ParamSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestApiScannerTest {

    private RestApiScanner scannerFor(Class<?>... controllers) {
        RequestMappingHandlerMapping mapping = SampleControllers.buildMapping(controllers);
        return new RestApiScanner(List.of(mapping), new ArchimedesApiProperties());
    }

    private RestApiScanner wrappedScannerFor(Class<?>... controllers) {
        RequestMappingHandlerMapping mapping = SampleControllers.buildMapping(controllers);
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.getResponseWrapper().setEnabled(true);
        props.getResponseWrapper().setWrapperClass(SampleControllers.ResultVo.class.getName());
        props.getResponseWrapper().setDataField("data");
        return new RestApiScanner(List.of(mapping), props);
    }

    private ApiInfo find(List<ApiInfo> apis, String path) {
        return apis.stream()
                .filter(a -> a.getPaths().contains(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no api for " + path));
    }

    @Test
    void scansPathsMethodsAndReturnType() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        ApiInfo getUser = find(apis, "/api/users/{id}");
        assertThat(getUser.getHttpMethods()).containsExactly("GET");
        assertThat(getUser.getControllerClass()).endsWith("UserController");
        assertThat(getUser.getHandlerMethod()).isEqualTo("getUser");
        assertThat(getUser.getReturnType()).isEqualTo("java.lang.String");
    }

    @Test
    void scansParametersWithSource() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        List<ParamInfo> params = find(apis, "/api/users/{id}").getParams();
        assertThat(params).extracting(ParamInfo::getName).contains("id", "filter");
        ParamInfo id = params.stream().filter(p -> p.getName().equals("id")).findFirst().orElseThrow();
        assertThat(id.getSource()).isEqualTo(ParamSource.PATH);
        assertThat(id.getType()).isEqualTo("java.lang.Long");
        assertThat(id.isRequired()).isTrue();
        ParamInfo filter = params.stream().filter(p -> p.getName().equals("filter")).findFirst().orElseThrow();
        assertThat(filter.getSource()).isEqualTo(ParamSource.QUERY);
        assertThat(filter.isRequired()).isFalse();

        ParamInfo body = find(apis, "/api/users").getParams().get(0);
        assertThat(body.getSource()).isEqualTo(ParamSource.BODY);
    }

    @Test
    void scansGenericReturnTypeAndDeprecated() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        ApiInfo legacy = find(apis, "/api/users/legacy");
        assertThat(legacy.getReturnType()).isEqualTo("java.util.List<java.lang.String>");
        assertThat(legacy.isDeprecated()).isTrue();
    }

    @Test
    void readsModuleAndDocFromOwnAnnotations() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();

        ApiInfo getUser = find(apis, "/api/users/{id}");
        assertThat(getUser.getSummary()).isEqualTo("查询用户");
        assertThat(getUser.getOperationDescription()).isEqualTo("按 ID 查询");
        assertThat(getUser.getTag()).isEqualTo("用户");
        assertThat(getUser.getTagDescription()).isEqualTo("用户管理");
    }

    @Test
    void deprecatedFromApiDocFlag() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        assertThat(find(apis, "/api/users/beta").isDeprecated()).isTrue();
        // 传统 @Deprecated 仍生效
        assertThat(find(apis, "/api/users/legacy").isDeprecated()).isTrue();
    }

    @Test
    void paramCarriesDescriptionAndExample() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        ParamInfo filter = find(apis, "/api/users/{id}").getParams().stream()
                .filter(p -> p.getName().equals("filter")).findFirst().orElseThrow();
        assertThat(filter.getDescription()).isEqualTo("过滤条件");
        assertThat(filter.getExample()).isEqualTo("active");
    }

    @Test
    void requiredFromApiParamAndResponses() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        ApiInfo getUser = find(apis, "/api/users/{id}");

        // 方法级 @ApiParam(name="id", required=true) 命中路径变量 id
        ParamInfo id = getUser.getParams().stream()
                .filter(p -> p.getName().equals("id")).findFirst().orElseThrow();
        assertThat(id.isRequired()).isTrue();

        // @ApiResponse 两条
        assertThat(getUser.getResponses()).extracting(r -> r.getCode())
                .containsExactlyInAnyOrder(200, 404);
    }

    @Test
    void requiredFallsBackToBindingWhenNoApiParam() {
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        // create 的 @RequestBody 无 @ApiParam → 必填回退绑定注解（@RequestBody 默认 required=true）
        ParamInfo body = find(apis, "/api/users").getParams().get(0);
        assertThat(body.isRequired()).isTrue();
    }

    @Test
    void apiParamCannotDowngradeBindingRequired() {
        // FIX1 守卫：detail 的 @PathVariable code 标了 @ApiParam 但未写 required（默认 false），
        // @ApiParam 只能上调必填、不能把绑定注解已确定的必填降为可选，故仍应 required==true。
        List<ApiInfo> apis = scannerFor(SampleControllers.UserController.class).scan();
        ParamInfo code = find(apis, "/api/users/detail/{code}").getParams().stream()
                .filter(p -> p.getName().equals("code")).findFirst().orElseThrow();
        assertThat(code.getSource()).isEqualTo(ParamSource.PATH);
        assertThat(code.isRequired()).isTrue();
    }

    @Test
    void cachesResult() {
        RestApiScanner scanner = scannerFor(SampleControllers.UserController.class);
        assertThat(scanner.scan()).isSameAs(scanner.scan());
    }

    @Test
    void responseSchemaWrappedIntoResultVo() {
        List<ApiInfo> apis = wrappedScannerFor(SampleControllers.WrapController.class).scan();
        ApiInfo list = find(apis, "/api/wrap/list");
        // 顶层为包装类 ResultVo，data 处为 List<String>（array=true, type=String）
        assertThat(list.getResponseSchema().getType()).isEqualTo("ResultVo");
        FieldInfo data = list.getResponseSchema().getChildren().stream()
                .filter(c -> c.getName().equals("data")).findFirst().orElseThrow();
        assertThat(data.isArray()).isTrue();
        assertThat(data.getType()).isEqualTo("String");
    }

    @Test
    void noApiWrapperEndpointNotWrapped() {
        List<ApiInfo> apis = wrappedScannerFor(SampleControllers.WrapController.class).scan();
        ApiInfo raw = find(apis, "/api/wrap/raw");
        // @NoApiWrapper：responseSchema 保持内层 List<String>（array=true, type=String），不套壳
        assertThat(raw.getResponseSchema().getType()).isEqualTo("String");
        assertThat(raw.getResponseSchema().isArray()).isTrue();
    }

    @Test
    void defaultScannerDoesNotWrap() {
        // 默认（未启用 wrapper）→ responseSchema 保持内层结构，向后兼容
        List<ApiInfo> apis = scannerFor(SampleControllers.WrapController.class).scan();
        ApiInfo list = find(apis, "/api/wrap/list");
        assertThat(list.getResponseSchema().getType()).isEqualTo("String");
        assertThat(list.getResponseSchema().isArray()).isTrue();
    }
}
