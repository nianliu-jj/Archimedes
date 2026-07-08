package io.github.nianliu.archimedes.scanner.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.nianliu.archimedes.annotation.ApiField;
import io.github.nianliu.archimedes.model.FieldInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TypeSchemaResolver 单测：嵌套/集合/Map/枚举/循环/包装解包与字段说明提取
 * （字段说明与必填改由自有注解 {@code @ApiField} 提供；Jackson @JsonProperty/@JsonIgnore
 * 的改名/剔除结构逻辑保持不变）。
 *
 * @author nianliu-jj
 * @since 2026-07-08
 */
class TypeSchemaResolverTest {

    enum OrderStatus { CREATED, PAID }

    static class OrderItem {
        @ApiField(value = "商品 ID", required = true)
        private Long productId;
        private int quantity;
    }

    static class CreateOrderRequest {
        @ApiField(value = "订单标题", required = true)
        private String title;
        @JsonProperty("order_no")
        private String orderNo;
        @JsonIgnore
        private String internalToken;
        private OrderStatus status;
        private List<OrderItem> items;
        private Map<String, OrderItem> extras;
        @ApiField("下单备注")
        private String remark;
    }

    static class TreeNode {
        private String label;
        private List<TreeNode> children;
    }

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

        FieldInfo title = child(root, "title");
        assertThat(title.getDescription()).isEqualTo("订单标题");
        assertThat(title.isRequired()).isTrue();
        assertThat(title.getType()).isEqualTo("String");

        assertThat(child(root, "order_no").getType()).isEqualTo("String");
        assertThat(root.getChildren()).noneMatch(c -> c.getName().equals("internalToken"));

        FieldInfo status = child(root, "status");
        assertThat(status.getType()).isEqualTo("OrderStatus");
        assertThat(status.getDescription()).contains("CREATED / PAID");

        FieldInfo items = child(root, "items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.getType()).isEqualTo("OrderItem");
        FieldInfo productId = child(items, "productId");
        assertThat(productId.getDescription()).isEqualTo("商品 ID");
        assertThat(productId.isRequired()).isTrue();
        assertThat(child(items, "quantity").getType()).isEqualTo("int");

        FieldInfo extras = child(root, "extras");
        assertThat(extras.getType()).isEqualTo("Map<String, OrderItem>");
        assertThat(extras.getChildren()).isNotEmpty();

        assertThat(child(root, "remark").getDescription()).isEqualTo("下单备注");
    }

    @Test
    void unwrapsCommonWrappers() throws Exception {
        FieldInfo wrapped = TypeSchemaResolver.resolve(returnType("wrappedList"));
        assertThat(wrapped.isArray()).isTrue();
        assertThat(wrapped.getType()).isEqualTo("OrderItem");
        assertThat(wrapped.getChildren()).extracting(FieldInfo::getName)
                .containsExactlyInAnyOrder("productId", "quantity");

        FieldInfo mono = TypeSchemaResolver.resolve(returnType("mono"));
        assertThat(mono.isArray()).isFalse();
        assertThat(mono.getType()).isEqualTo("OrderItem");

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
        assertThat(children.getDescription()).contains("递归引用");
        assertThat(children.getChildren()).isEmpty();
    }
}
