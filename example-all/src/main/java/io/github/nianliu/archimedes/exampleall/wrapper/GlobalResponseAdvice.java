package io.github.nianliu.archimedes.exampleall.wrapper;

import io.github.nianliu.archimedes.annotation.NoApiWrapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应包装：把 Controller 返回值包进 {@link ResultVo}。
 * 已是 ResultVo、或标了 {@link NoApiWrapper} 的接口不包装——与 Archimedes 契约展示的豁免规则一致。
 *
 * <p>作用域刻意限定在本演示包（{@code basePackages = ...exampleall.wrapper}）：
 * <ul>
 *   <li>避免包裹 Archimedes 自身的管理端点（{@code /archimedes/apis}、UI、日志查询等）——
 *       真实项目里管理/观测类端点同理应排除；</li>
 *   <li>避免误包裹返回裸 {@code String} 的端点（如本工程的 /trace/*）——
 *       {@code StringHttpMessageConverter} 收到非 String 的 ResultVo 会抛 ClassCastException。</li>
 * </ul>
 *
 * @author nianliu-jj
 * @since 2026-07-10
 */
@RestControllerAdvice(basePackages = "io.github.nianliu.archimedes.exampleall.wrapper")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 返回类型已是 ResultVo、或方法/类标了 @NoApiWrapper 的不包装
        if (ResultVo.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        return !returnType.hasMethodAnnotation(NoApiWrapper.class)
                && returnType.getDeclaringClass().getAnnotation(NoApiWrapper.class) == null;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return new ResultVo(body);
    }
}
