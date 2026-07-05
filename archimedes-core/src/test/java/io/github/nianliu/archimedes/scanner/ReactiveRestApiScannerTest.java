package io.github.nianliu.archimedes.scanner;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.model.ApiInfo;
import io.github.nianliu.archimedes.model.ParamInfo;
import io.github.nianliu.archimedes.model.ParamSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reactive 扫描器单测：直接向 reactive RequestMappingHandlerMapping 注册映射
 * 驱动真实提取路径（无需启动服务器）。
 *
 * @author nianliu-jj
 * @since 2026-07-05
 */
class ReactiveRestApiScannerTest {

    static class DemoHandler {
        public Mono<String> greet(@RequestParam("name") String name) {
            return Mono.just("hi " + name);
        }

        public Flux<Integer> stream(@PathVariable("id") Long id, @RequestBody String payload) {
            return Flux.just(1);
        }

        public Mono<Void> self() {
            return Mono.empty();
        }
    }

    private static RequestMappingHandlerMapping mappingOf(Object[]... registrations) throws Exception {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        DemoHandler handler = new DemoHandler();
        for (Object[] r : registrations) {
            Method method = DemoHandler.class.getMethod((String) r[2], (Class<?>[]) r[3]);
            mapping.registerMapping(
                    RequestMappingInfo.paths((String) r[0]).methods((RequestMethod) r[1]).build(),
                    handler, method);
        }
        return mapping;
    }

    private static Object[] reg(String path, RequestMethod method, String name, Class<?>... paramTypes) {
        return new Object[]{path, method, name, paramTypes};
    }

    @Test
    void scansReactiveMappingsWithParamsAndReturnTypes() throws Exception {
        RequestMappingHandlerMapping mapping = mappingOf(
                reg("/reactive/greet", RequestMethod.GET, "greet", String.class),
                reg("/reactive/items/{id}", RequestMethod.POST, "stream", Long.class, String.class));

        List<ApiInfo> apis = new ReactiveRestApiScanner(List.of(mapping), new ArchimedesApiProperties()).scan();

        assertThat(apis).hasSize(2);
        ApiInfo greet = apis.stream().filter(a -> a.getPaths().contains("/reactive/greet"))
                .findFirst().orElseThrow();
        assertThat(greet.getHttpMethods()).containsExactly("GET");
        assertThat(greet.getReturnType()).isEqualTo("reactor.core.publisher.Mono<java.lang.String>");
        assertThat(greet.getParams()).singleElement().satisfies(p -> {
            assertThat(p.getName()).isEqualTo("name");
            assertThat(p.getSource()).isEqualTo(ParamSource.QUERY);
            assertThat(p.isRequired()).isTrue();
        });

        ApiInfo stream = apis.stream().filter(a -> a.getPaths().contains("/reactive/items/{id}"))
                .findFirst().orElseThrow();
        assertThat(stream.getHttpMethods()).containsExactly("POST");
        assertThat(stream.getReturnType()).isEqualTo("reactor.core.publisher.Flux<java.lang.Integer>");
        assertThat(stream.getParams()).extracting(ParamInfo::getSource)
                .containsExactly(ParamSource.PATH, ParamSource.BODY);
    }

    @Test
    void excludesSelfBasePathEndpoints() throws Exception {
        RequestMappingHandlerMapping mapping = mappingOf(
                reg("/archimedes", RequestMethod.GET, "self"),
                reg("/archimedes/apis", RequestMethod.GET, "self"),
                reg("/reactive/greet", RequestMethod.GET, "greet", String.class));

        List<ApiInfo> apis = new ReactiveRestApiScanner(List.of(mapping), new ArchimedesApiProperties()).scan();

        assertThat(apis).hasSize(1);
        assertThat(apis.get(0).getPaths()).containsExactly("/reactive/greet");
    }

    @Test
    void cachesResult() throws Exception {
        ReactiveRestApiScanner scanner = new ReactiveRestApiScanner(
                List.of(mappingOf(reg("/reactive/greet", RequestMethod.GET, "greet", String.class))),
                new ArchimedesApiProperties());
        assertThat(scanner.scan()).isSameAs(scanner.scan());
    }
}
