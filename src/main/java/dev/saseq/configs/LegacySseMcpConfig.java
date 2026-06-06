package dev.saseq.configs;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerChangeNotificationProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcSseServerTransportProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "stdio", havingValue = "false")
@EnableConfigurationProperties(McpServerSseProperties.class)
public class LegacySseMcpConfig {

    @Bean
    public LegacySseMcpEndpoint legacySseMcpEndpoint(
            JsonMapper jsonMapper,
            McpServerSseProperties sseProperties,
            McpServerProperties serverProperties,
            McpServerChangeNotificationProperties changeNotificationProperties,
            ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> tools,
            ObjectProvider<List<McpServerFeatures.SyncResourceSpecification>> resources,
            ObjectProvider<List<McpServerFeatures.SyncResourceTemplateSpecification>> resourceTemplates,
            ObjectProvider<List<McpServerFeatures.SyncPromptSpecification>> prompts,
            ObjectProvider<List<McpServerFeatures.SyncCompletionSpecification>> completions,
            ObjectProvider<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers,
            ObjectProvider<McpSyncServerCustomizer> syncServerCustomizers) {

        WebMvcSseServerTransportProvider transportProvider = WebMvcSseServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .baseUrl(sseProperties.getBaseUrl())
                .sseEndpoint(sseProperties.getSseEndpoint())
                .messageEndpoint(sseProperties.getSseMessageEndpoint())
                .keepAliveInterval(sseProperties.getKeepAliveInterval())
                .build();

        McpSyncServer server = new McpServerAutoConfiguration().mcpSyncServer(
                transportProvider,
                McpSchema.ServerCapabilities.builder(),
                serverProperties,
                changeNotificationProperties,
                tools,
                resources,
                resourceTemplates,
                prompts,
                completions,
                rootsChangeConsumers,
                Optional.of(spec -> syncServerCustomizers.orderedStream().forEach(customizer -> customizer.customize(spec))));

        return new LegacySseMcpEndpoint(transportProvider, server);
    }

    @Bean
    public RouterFunction<ServerResponse> legacySseMcpRouterFunction(LegacySseMcpEndpoint endpoint) {
        return endpoint.transportProvider().getRouterFunction();
    }

    public record LegacySseMcpEndpoint(WebMvcSseServerTransportProvider transportProvider, McpSyncServer server) {
    }
}
