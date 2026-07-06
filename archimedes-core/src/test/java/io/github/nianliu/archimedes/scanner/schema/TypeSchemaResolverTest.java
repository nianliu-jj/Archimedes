package io.github.nianliu.archimedes.scanner.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.nianliu.archimedes.model.FieldInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TypeSchemaResolver 单测：嵌套/集合/Map/枚举/循环/包装解包与注解说明提取
 * （Swagger v3 与 jakarta NotNull 走同 FQCN 桩注解，Jackson 用真实注解）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
class TypeSchemaResolverTest {

    enum OrderStatus { CREATED, PAID }

    static class OrderItem {
        @Schema(description = "商品 ID", requiredMode = "REQUIRED")
        private Long productId;
        private int quantity;
    }

    static class CreateOrderRequest {
        @NotNull
        @Schema(description = "订单标题")
        private String title;
        @JsonProperty("order_no")
        private String orderNo;
        @JsonIgnore
        private String internalToken;
        private OrderStatus status;
        private List<OrderItem> items;
        private Map<String, OrderItem> extras;
        @JsonPropertyDescription("下单备注")
        private String remark;
    }

    static class TreeNode {
        private String label;
        private List<TreeNode> children;
    }

    /** 泛型返回类型的取样器：经反射拿到带泛型的 Type 实例。 */
    static class Holder {
        ResponseEntity<List<OrderItem>> wrappedList() { return null; }
        Mono<OrderItem> mono() { return null; }
        void nothing() { }
        String scalar() { return null; }
    }

    private static Type returnType(String method) throws Exception {
        return Holder.class.getDeclaredMethod(method).getGenericReturnType();
    }

    private static FieldInfo child(FieldInfo node, String name) {
        return node.getChildren().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name));
    }

    @Test
    void resolvesNestedPojoWithAnnotations() {
        FieldInfo root = TypeSchemaResolver.resolve(CreateOrderRequest.class);

        assertThat(root).isNotNull();
        assertThat(root.getType()).isEqualTo("CreateOrderRequest");
        assertThat(root.isArray()).isFalse();

        // Swagger @Schema 说明 + jakarta @NotNull 必填
        FieldInfo title = child(root, "title");
        assertThat(title.getDescription()).isEqualTo("订单标题");
        assertThat(title.isRequired()).isTrue();
        assertThat(title.getType()).isEqualTo("String");

        // @JsonProperty 改名、@JsonIgnore 剔除
        assertThat(child(root, "order_no").getType()).isEqualTo("String");
        assertThat(root.getChildren()).noneMatch(c -> c.getName().equals("internalToken"));

        // 枚举自动列出可选值
        FieldInfo status = child(root, "status");
        assertThat(status.getType()).isEqualTo("OrderStatus");
        assertThat(status.getDescription()).contains("CREATED / PAID");

        // 集合字段：array 标记 + 元素字段为 children（@Schema requiredMode=REQUIRED 生效）
        FieldInfo items = child(root, "items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.getType()).isEqualTo("OrderItem");
        FieldInfo productId = child(items, "productId");
        assertThat(productId.getDescription()).isEqualTo("商品 ID");
        assertThat(productId.isRequired()).isTrue();
        assertThat(child(items, "quantity").getType()).isEqualTo("int");

        // Map：键值简名展示 + 值类型字段为 children
        FieldInfo extras = child(root, "extras");
        assertThat(extras.getType()).isEqualTo("Map<String, OrderItem>");
        assertThat(extras.getChildren()).isNotEmpty();

        // Jackson @JsonPropertyDescription
        assertThat(child(root, "remark").getDescription()).isEqualTo("下单备注");
    }

    @Test
    void unwrapsCommonWrappers() throws Exception {
        // ResponseEntity<List<OrderItem>> → OrderItem 元素树 + array 标记
        FieldInfo wrapped = TypeSchemaResolver.resolve(returnType("wrappedList"));
        assertThat(wrapped.isArray()).isTrue();
        assertThat(wrapped.getType()).isEqualTo("OrderItem");
        assertThat(wrapped.getChildren()).extracting(FieldInfo::getName)
                .containsExactlyInAnyOrder("productId", "quantity");

        // Mono<OrderItem> → OrderItem（非集合）
        FieldInfo mono = TypeSchemaResolver.resolve(returnType("mono"));
        assertThat(mono.isArray()).isFalse();
        assertThat(mono.getType()).isEqualTo("OrderItem");

        // void → 无结构；标量 → 无 children 的叶子根
        assertThat(TypeSchemaResolver.resolve(returnType("nothing"))).isNull();
        FieldInfo scalar = TypeSchemaResolver.resolve(returnType("scalar"));
        assertThat(scalar.getType()).isEqualTo("String");
        assertThat(scalar.getChildren()).isEmpty();
    }

    @Test
    void guardsAgainstSelfReference() {
        FieldInfo root = TypeSchemaResolver.resolve(TreeNode.class);

        FieldInfo children = child(root, "children");
        assertThat(children.isArray()).isTrue();
        assertThat(children.getType()).isEqualTo("TreeNode");
        // 递归引用处落叶子并注记，不再展开
        assertThat(children.getDescription()).contains("递归引用");
        assertThat(children.getChildren()).isEmpty();
    }
}
