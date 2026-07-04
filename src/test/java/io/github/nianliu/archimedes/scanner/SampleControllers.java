package io.github.nianliu.archimedes.scanner;

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
    public static class UserController {

        @GetMapping("/{id}")
        public String getUser(@PathVariable Long id, @RequestParam(required = false) String filter) {
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
