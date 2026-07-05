package io.github.nianliu.archimedes.boot3;

import io.github.nianliu.archimedes.config.ArchimedesApiProperties;
import io.github.nianliu.archimedes.scanner.RestApiScanner;
import io.github.nianliu.archimedes.scanner.ws.WebSocketApiContributor;
import io.github.nianliu.archimedes.web.ArchimedesApiController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.web.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;

class ArchimedesAutoConfigurationTest {

    @Test
    void registersBeansInServletWebApp() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RestApiScanner.class);
                    assertThat(context).hasSingleBean(ArchimedesApiController.class);
                    assertThat(context).hasSingleBean(ArchimedesApiProperties.class);
                });
    }

    @Test
    void registersWebSocketContributorsWhenClassesPresent() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .run(context ->
                        assertThat(context.getBeansOfType(WebSocketApiContributor.class)).hasSize(3));
    }

    @Test
    void skipsWebSocketScannersWithoutWebSocketClasses() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(WebSocketHandler.class,
                        SimpAnnotationMethodMessageHandler.class,
                        jakarta.websocket.server.ServerEndpoint.class))
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ArchimedesApiController.class);
                    assertThat(context.getBeansOfType(WebSocketApiContributor.class)).isEmpty();
                });
    }

    @Test
    void skipsInNonWebApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(RestApiScanner.class));
    }

    @Test
    void skipsWhenDisabled() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ArchimedesAutoConfiguration.class))
                .withPropertyValues("archimedes.api.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RestApiScanner.class));
    }
}
