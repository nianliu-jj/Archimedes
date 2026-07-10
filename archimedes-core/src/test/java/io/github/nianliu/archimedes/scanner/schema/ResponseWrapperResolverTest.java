package io.github.nianliu.archimedes.scanner.schema;

import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.FieldInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResponseWrapperResolver 单测：包装组装（data 节点替换）、三类豁免、降级路径。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
class ResponseWrapperResolverTest {

    /** 测试用包装类：code/msg/data 三字段（data 承载真实返回对象）。 */
    static class ResultVo {
        private int code;
        private String msg;
        private Object data;
    }

    static class Payload {
        private String name;
        private int qty;
    }

    static class Handlers {
        Payload wrapped() { return null; }
        @NoApiWrapper
        Payload exemptByAnnotation() { return null; }
        ResultVo returnsWrapper() { return null; }
        ResponseEntity<Payload> returnsResponseEntity() { return null; }
        void nothing() { }
    }

    @NoApiWrapper
    static class ExemptController {
        Payload any() { return null; }
    }

    private ArchimedesApiProperties propsWith(String wrapperClass, String dataField) {
        ArchimedesApiProperties props = new ArchimedesApiProperties();
        props.getResponseWrapper().setEnabled(true);
        props.getResponseWrapper().setWrapperClass(wrapperClass);
        props.getResponseWrapper().setDataField(dataField);
        return props;
    }

    private static Method m(String name, Class<?>... args) throws Exception {
        return Handlers.class.getDeclaredMethod(name, args);
    }

    private static FieldInfo child(FieldInfo node, String name) {
        return node.getChildren().stream().filter(c -> c.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name));
    }

    @Test
    void wrapsInnerIntoDataField() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        FieldInfo wrapped = r.wrap(inner, m("wrapped"), Handlers.class);

        // 顶层变成包装类
        assertThat(wrapped.getType()).isEqualTo("ResultVo");
        assertThat(wrapped.getChildren()).extracting(FieldInfo::getName).contains("code", "msg", "data");
        // data 节点结构被替换为 Payload
        FieldInfo data = child(wrapped, "data");
        assertThat(data.getType()).isEqualTo("Payload");
        assertThat(data.getChildren()).extracting(FieldInfo::getName).containsExactlyInAnyOrder("name", "qty");
        // 外壳其余字段保留
        assertThat(child(wrapped, "code").getType()).isEqualTo("int");
    }

    @Test
    void exemptByNoApiWrapperMethod() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        FieldInfo out = r.wrap(inner, m("exemptByAnnotation"), Handlers.class);
        assertThat(out.getType()).isEqualTo("Payload"); // 未套壳
    }

    @Test
    void exemptByNoApiWrapperOnClass() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        Method any = ExemptController.class.getDeclaredMethod("any");
        FieldInfo out = r.wrap(inner, any, ExemptController.class);
        assertThat(out.getType()).isEqualTo("Payload");
    }

    @Test
    void exemptWhenReturnTypeIsWrapper() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(ResultVo.class);
        FieldInfo out = r.wrap(inner, m("returnsWrapper"), Handlers.class);
        assertThat(out.getType()).isEqualTo("ResultVo");
        // 未二次包装：data 仍是包装类原始 Object 叶子，不是被替换的结构
        assertThat(child(out, "data").getType()).isEqualTo("Object");
    }

    @Test
    void exemptWhenReturnTypeIsResponseEntity() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        // 内层已解包为 Payload（resolve 解包 ResponseEntity）
        FieldInfo inner = TypeSchemaResolver.resolve(
                m("returnsResponseEntity").getGenericReturnType());
        FieldInfo out = r.wrap(inner, m("returnsResponseEntity"), Handlers.class);
        assertThat(out.getType()).isEqualTo("Payload"); // 未套壳
    }

    @Test
    void disabledReturnsInner() throws Exception {
        ArchimedesApiProperties props = new ArchimedesApiProperties(); // 默认关闭
        ResponseWrapperResolver r = new ResponseWrapperResolver(props);
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        assertThat(r.wrap(inner, m("wrapped"), Handlers.class)).isSameAs(inner);
    }

    @Test
    void wrapperClassNotLoadableReturnsInner() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith("com.nope.NotExist", "data"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        assertThat(r.wrap(inner, m("wrapped"), Handlers.class)).isSameAs(inner);
    }

    @Test
    void missingDataFieldReturnsInner() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "payload"));
        FieldInfo inner = TypeSchemaResolver.resolve(Payload.class);
        assertThat(r.wrap(inner, m("wrapped"), Handlers.class)).isSameAs(inner);
    }

    @Test
    void voidInnerLeavesDataNodeAsIs() throws Exception {
        ResponseWrapperResolver r = new ResponseWrapperResolver(propsWith(ResultVo.class.getName(), "data"));
        // 内层 void → resolve 返回 null
        FieldInfo out = r.wrap(null, m("nothing"), Handlers.class);
        assertThat(out.getType()).isEqualTo("ResultVo");
        assertThat(child(out, "data").getType()).isEqualTo("Object"); // data 原样
    }
}
