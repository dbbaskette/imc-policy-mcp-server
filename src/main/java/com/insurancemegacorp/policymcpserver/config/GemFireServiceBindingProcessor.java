package com.insurancemegacorp.policymcpserver.config;

import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Environment post-processor to configure GemFire VectorStore from Cloud Foundry service bindings.
 *
 * Extracts connection details from the p-cloudcache service (imc-cache) and maps them to
 * Spring AI GemFire properties.
 */
public class GemFireServiceBindingProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GemFireServiceBindingProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Only run when cloud profile is active
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("cloud"))) {
            logger.debug("Skipping GemFire service binding processor - cloud profile not active");
            return;
        }

        try {
            CfEnv cfEnv = new CfEnv();

            // Look for p-cloudcache service
            List<CfService> gemfireServices = cfEnv.findServicesByLabel("p-cloudcache");

            if (gemfireServices.isEmpty()) {
                logger.warn("No p-cloudcache service found in VCAP_SERVICES - GemFire cache will not be available");
                return;
            }

            CfService gemfireService = gemfireServices.get(0);
            logger.info("Found p-cloudcache service: {}", gemfireService.getName());

            // Extract URLs from credentials
            Map<String, Object> credentials = gemfireService.getCredentials().getMap();

            // Get the vectordb URL from the urls map
            @SuppressWarnings("unchecked")
            Map<String, String> urls = (Map<String, String>) credentials.get("urls");

            if (urls == null || urls.isEmpty()) {
                logger.warn("No URLs map found in p-cloudcache service credentials");
                return;
            }

            // Get the vectordb URL
            String gemfireUrl = urls.get("vectordb");

            if (gemfireUrl == null) {
                logger.warn("No vectordb URL found in p-cloudcache service credentials");
                return;
            }

            if (gemfireUrl != null) {
                logger.info("Configuring GemFire VectorStore with URL: {}", gemfireUrl);

                // Parse URL to extract host and port
                String host;
                int port;

                if (gemfireUrl.startsWith("http://") || gemfireUrl.startsWith("https://")) {
                    // Full URL - extract host and port
                    String protocol = gemfireUrl.startsWith("https://") ? "https://" : "http://";
                    String urlWithoutProtocol = gemfireUrl.substring(protocol.length());

                    int pathIndex = urlWithoutProtocol.indexOf('/');
                    String hostPort = pathIndex > 0 ?
                            urlWithoutProtocol.substring(0, pathIndex) :
                            urlWithoutProtocol;

                    int colonIndex = hostPort.indexOf(':');
                    if (colonIndex > 0) {
                        host = hostPort.substring(0, colonIndex);
                        String portStr = hostPort.substring(colonIndex + 1);
                        port = Integer.parseInt(portStr);
                    } else {
                        host = hostPort;
                        port = protocol.equals("https://") ? 443 : 80;
                    }
                } else {
                    // Assume it's just a host
                    host = gemfireUrl;
                    port = 8080;
                }

                // Extract credentials (use developer role for read/write access)
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> users = (List<Map<String, Object>>) credentials.get("users");

                String username = null;
                String password = null;

                if (users != null && !users.isEmpty()) {
                    // Look for developer user
                    for (Map<String, Object> user : users) {
                        @SuppressWarnings("unchecked")
                        List<String> roles = (List<String>) user.get("roles");
                        if (roles != null && roles.contains("developer")) {
                            username = (String) user.get("username");
                            password = (String) user.get("password");
                            break;
                        }
                    }
                }

                // Set Spring properties
                Map<String, Object> gemfireProps = new HashMap<>();
                gemfireProps.put("spring.ai.vectorstore.gemfire.host", host);
                gemfireProps.put("spring.ai.vectorstore.gemfire.port", port);

                if (username != null && password != null) {
                    gemfireProps.put("spring.ai.vectorstore.gemfire.username", username);
                    gemfireProps.put("spring.ai.vectorstore.gemfire.password", password);
                    logger.info("Configured GemFire authentication with user: {}", username);
                }

                MapPropertySource propertySource = new MapPropertySource(
                        "gemfireServiceBinding", gemfireProps);

                environment.getPropertySources().addLast(propertySource);

                logger.info("Configured GemFire properties: host={}, port={}", host, port);
            }

        } catch (Exception e) {
            logger.warn("Could not process p-cloudcache service binding: {}", e.getMessage());
            // Don't fail startup - just log the warning
        }
    }
}
