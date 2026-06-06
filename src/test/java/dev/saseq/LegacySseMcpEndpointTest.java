package dev.saseq;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;

@SpringBootTest(properties = {
        "spring.profiles.active=http",
        "spring.ai.mcp.server.stdio=false",
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "spring.main.web-application-type=servlet",
        "discord.jda.enabled=false"
})
class LegacySseMcpEndpointTest {

    private final List<RouterFunction<ServerResponse>> routerFunctions;
    private ServerRequest sseRequest;

    @Autowired
    LegacySseMcpEndpointTest(List<RouterFunction<ServerResponse>> routerFunctions) {
        this.routerFunctions = routerFunctions;
    }

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sse");
        request.addHeader(HttpHeaders.ACCEPT, TEXT_EVENT_STREAM_VALUE);
        sseRequest = ServerRequest.create(request, List.of());
    }

    @Test
    void sseEndpointReturnsEventStream() throws Exception {
        Optional<HandlerFunction<ServerResponse>> handler = routerFunctions.stream()
                .map(routerFunction -> routerFunction.route(sseRequest))
                .flatMap(Optional::stream)
                .findFirst();

        assertThat(handler).isPresent();

        ServerResponse response = handler.orElseThrow().handle(sseRequest);

        assertThat(response.statusCode().value()).isEqualTo(200);
        assertThat(response.headers().getContentType()).isNotNull();
        assertThat(response.headers().getContentType().isCompatibleWith(TEXT_EVENT_STREAM)).isTrue();
    }

    @TestConfiguration
    static class TestDiscordConfig {

        @Bean
        JDA jda() {
            return (JDA) Proxy.newProxyInstance(
                    JDA.class.getClassLoader(),
                    new Class<?>[]{JDA.class},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> "JDA test proxy";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                default -> throw new UnsupportedOperationException(method.getName());
                            };
                        }
                        throw new UnsupportedOperationException("JDA is not used by the SSE smoke test");
                    });
        }
    }
}
