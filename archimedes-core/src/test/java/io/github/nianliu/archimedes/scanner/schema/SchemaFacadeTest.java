package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiField;
import io.github.nianliu.archimedes.annotation.ApiModule;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFacadeTest {

    @ApiModule("订单")
    static class ValueAliasModule { }

    @ApiModule(name = "订单管理", description = "订单域")
    static class NamedModule { }

    static class Handlers {
        @ApiDoc(value = "别名摘要")
        void aliased() { }

        @ApiDoc(summary = "创建", description = "下单", deprecated = true)
        void full() { }

        void bare(@ApiField(value = "关键字", example = "kw") String q) { }
    }

    private static Annotation[] method(String name) throws Exception {
        return Handlers.class.getDeclaredMethod(name).getAnnotations();
    }

    @Test
    void moduleNameFallsBackToValue() {
        assertThat(TypeSchemaResolver.tagName(
                ValueAliasModule.class.getAnnotations(), ValueAliasModule.class.getName()))
                .isEqualTo("订单");
        assertThat(TypeSchemaResolver.tagName(
                NamedModule.class.getAnnotations(), NamedModule.class.getName()))
                .isEqualTo("订单管理");
        assertThat(TypeSchemaResolver.tagDescription(NamedModule.class.getAnnotations()))
                .isEqualTo("订单域");
    }

    @Test
    void moduleNameFallsBackToClassSimpleNameWithoutAnnotation() {
        assertThat(TypeSchemaResolver.tagName(new Annotation[0],
                "com.demo.OrderController")).isEqualTo("Order");
    }

    @Test
    void docSummaryFallsBackToValueAndReadsDeprecated() throws Exception {
        assertThat(TypeSchemaResolver.operationSummary(method("aliased"))).isEqualTo("别名摘要");
        assertThat(TypeSchemaResolver.operationSummary(method("full"))).isEqualTo("创建");
        assertThat(TypeSchemaResolver.operationDescription(method("full"))).isEqualTo("下单");
        assertThat(TypeSchemaResolver.operationDeprecated(method("full"))).isTrue();
        assertThat(TypeSchemaResolver.operationDeprecated(method("aliased"))).isFalse();
    }

    @Test
    void paramDescriptionAndExample() throws Exception {
        Annotation[] paramAnns = Handlers.class
                .getDeclaredMethod("bare", String.class)
                .getParameterAnnotations()[0];
        assertThat(TypeSchemaResolver.paramDescription(paramAnns)).isEqualTo("关键字");
        assertThat(TypeSchemaResolver.paramExample(paramAnns)).isEqualTo("kw");
    }
}
