package io.github.nianliu.archimedes.exampleall.wrapper;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 统一响应包装体演示：普通端点被 ResultVo 包裹，@NoApiWrapper 端点保持裸结构。
 * 打开 UI 对照两者的响应字段树。
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
@RestController
@RequestMapping("/api/wrapper")
@ApiModule(name = "响应包装演示", description = "演示统一响应包装体在契约中的展示与 @NoApiWrapper 豁免")
public class WrapperDemoController {

    /** 被 ResultVo 包裹：契约 responseSchema 顶层应为 ResultVo，data 处为 Item 结构。 */
    @ApiDoc(summary = "查询条目（被包装）", description = "返回值经 ResponseBodyAdvice 包进 ResultVo")
    @GetMapping("/items")
    public List<Item> items() {
        return Arrays.asList(new Item(1L, "键盘"), new Item(2L, "鼠标"));
    }

    /** 豁免：契约 responseSchema 保持裸 Item 结构。 */
    @ApiDoc(summary = "查询条目（不包装）", description = "标 @NoApiWrapper，响应不套 ResultVo")
    @NoApiWrapper
    @GetMapping("/items-raw")
    public List<Item> itemsRaw() {
        return Arrays.asList(new Item(3L, "显示器"));
    }

    /** 演示用条目。 */
    public static class Item {
        private Long id;
        private String name;

        public Item() {
        }

        public Item(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
