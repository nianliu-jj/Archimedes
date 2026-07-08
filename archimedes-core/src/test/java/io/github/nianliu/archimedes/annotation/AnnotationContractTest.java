package io.github.nianliu.archimedes.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationContractTest {

    @ApiModule(value = "订单", description = "订单管理")
    static class Sample {
        @ApiField(value = "商品ID", required = true, example = "1001")
        private Long itemId;

        // @ApiDoc 的 @Target 为 METHOD，只能标注在方法上（brief 原稿误置于类上导致无法编译）。
        @ApiDoc(summary = "创建订单", description = "下单并返回单号", deprecated = true)
        void create() {
        }
    }

    @Test
    void apiModuleRetainedAtRuntimeWithAttributes() {
        ApiModule m = Sample.class.getAnnotation(ApiModule.class);
        assertThat(m).isNotNull();
        assertThat(m.value()).isEqualTo("订单");
        assertThat(m.name()).isEmpty();
        assertThat(m.description()).isEqualTo("订单管理");
        assertThat(ApiModule.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(Arrays.asList(ApiModule.class.getAnnotation(Target.class).value()))
                .containsExactly(ElementType.TYPE);
    }

    @Test
    void apiDocRetainedAtRuntimeWithAttributes() throws Exception {
        ApiDoc d = Sample.class.getDeclaredMethod("create").getAnnotation(ApiDoc.class);
        assertThat(d.summary()).isEqualTo("创建订单");
        assertThat(d.description()).isEqualTo("下单并返回单号");
        assertThat(d.deprecated()).isTrue();
        assertThat(Arrays.asList(ApiDoc.class.getAnnotation(Target.class).value()))
                .containsExactly(ElementType.METHOD);
    }

    @Test
    void apiFieldTargetsParameterAndField() throws Exception {
        ApiField f = Sample.class.getDeclaredField("itemId").getAnnotation(ApiField.class);
        assertThat(f.value()).isEqualTo("商品ID");
        assertThat(f.required()).isTrue();
        assertThat(f.example()).isEqualTo("1001");
        assertThat(Arrays.asList(ApiField.class.getAnnotation(Target.class).value()))
                .containsExactlyInAnyOrder(ElementType.PARAMETER, ElementType.FIELD);
    }
}
