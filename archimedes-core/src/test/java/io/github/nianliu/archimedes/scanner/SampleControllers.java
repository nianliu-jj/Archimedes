package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.annotation.ApiDoc;
import io.github.nianliu.archimedes.annotation.ApiModule;
import io.github.nianliu.archimedes.annotation.ApiParam;
import io.github.nianliu.archimedes.annotation.ApiResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/** 测试用样例 Controller 与构造 RequestMappingHandlerMapping 的辅助方法。 */
public final class SampleControllers {

    private SampleControllers() {
    }

    @RestController
    @RequestMapping("/api/users")
    @ApiModule(name = "用户", description = "用户管理")
    public static class UserController {

        @ApiDoc(summary = "查询用户", description = "按 ID 查询")
        @ApiParam(name = "id", required = true, value = "用户 ID")
        @ApiResponse(code = 200, description = "命中用户")
        @ApiResponse(code = 404, description = "用户不存在")
        @GetMapping("/{id}")
        public String getUser(@PathVariable Long id,
                @ApiParam(value = "过滤条件", example = "active") @RequestParam(required = false) String filter) {
            return "";
        }

        @PostMapping
        public String create(@RequestBody String body) {
            return "";
        }

        @Deprecated
        @GetMapping("/legacy")
        public List<String> legacy() {
            return List.of();
        }

        @ApiDoc(summary = "试验接口", deprecated = true)
        @GetMapping("/beta")
        public String beta() {
            return "";
        }

        /**
         * detail：@PathVariable + @ApiParam 但未写 required（默认 false）——
         * 用于守卫 FIX1：绑定注解已定的必填不得被 @ApiParam 静默降级。
         */
        @GetMapping("/detail/{code}")
        public String detail(@ApiParam(value = "编码", example = "C-1") @PathVariable String code) {
            return "";
        }
    }

    /** 用给定 Controller 类构造并初始化一个 RequestMappingHandlerMapping。 */
    public static RequestMappingHandlerMapping buildMapping(Class<?>... controllers) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        for (Class<?> controller : controllers) {
            context.register(controller);
        }
        context.refresh();

        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.setApplicationContext(context);
        mapping.afterPropertiesSet();
        return mapping;
    }
}
