package com.baskettecase.mcpserver.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * Database configuration for local development using Testcontainers
 */
@Configuration
@Profile({"local-sse", "local-stdio"})
public class DatabaseConfiguration implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static PostgreSQLContainer<?> postgresContainer;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        
        // Only configure for local profiles
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isLocalProfile = false;
        for (String profile : activeProfiles) {
            if (profile.equals("local-sse") || profile.equals("local-stdio")) {
                isLocalProfile = true;
                break;
            }
        }
        
        if (!isLocalProfile) {
            return;
        }

        // Check if Docker is available
        if (!isDockerAvailable()) {
            System.err.println("❌ Docker is not available!");
            System.err.println("🐳 Please start Docker Desktop and try again.");
            System.err.println("");
            System.err.println("Local profiles require PostgreSQL with PGVector extension,");
            System.err.println("which is provided via Docker containers.");
            System.exit(1);
        }

        Map<String, Object> properties = new HashMap<>();
        
        if (postgresContainer == null) {
            try {
                // Use pgvector-enabled PostgreSQL image
                postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("policy_rag_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true); // Reuse container across restarts for faster development
                
                postgresContainer.start();
                
                System.out.println("🐘 PostgreSQL with PGVector started at: " + postgresContainer.getJdbcUrl());
                
                // Configure PostgreSQL properties
                properties.put("spring.datasource.url", postgresContainer.getJdbcUrl());
                properties.put("spring.datasource.username", postgresContainer.getUsername());
                properties.put("spring.datasource.password", postgresContainer.getPassword());
                properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
                properties.put("spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
                
                // Configure vector database properties
                properties.put("spring.ai.vectordatabase.pgvector.host", postgresContainer.getHost());
                properties.put("spring.ai.vectordatabase.pgvector.port", postgresContainer.getMappedPort(5432));
                properties.put("spring.ai.vectordatabase.pgvector.database", postgresContainer.getDatabaseName());
                properties.put("spring.ai.vectordatabase.pgvector.username", postgresContainer.getUsername());
                properties.put("spring.ai.vectordatabase.pgvector.password", postgresContainer.getPassword());
                properties.put("spring.ai.vectordatabase.pgvector.initialize-schema", "true");
                
            } catch (Exception e) {
                System.err.println("❌ Failed to start PostgreSQL container: " + e.getMessage());
                System.err.println("🐳 Please ensure Docker Desktop is running and try again.");
                System.exit(1);
            }
        }
        
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(
                new MapPropertySource("database-config", properties)
            );
        }
    }
    
    private boolean isDockerAvailable() {
        try {
            // Try to access Docker client
            org.testcontainers.DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    

    @PreDestroy
    public void cleanup() {
        if (postgresContainer != null && postgresContainer.isRunning()) {
            // Don't stop if reuse is enabled - let Testcontainers handle lifecycle
            if (!postgresContainer.isShouldBeReused()) {
                postgresContainer.stop();
            }
        }
    }
}