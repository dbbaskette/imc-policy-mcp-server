package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Command line runner that triggers re-embedding when the RE_EMBED_DATA property is set to true
 */
@Component
@ConditionalOnProperty(name = "app.data.re-embed", havingValue = "true")
public class ReEmbeddingCommandLineRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReEmbeddingCommandLineRunner.class);

    @Autowired
    private ReEmbeddingService reEmbeddingService;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        logger.info("RE-EMBED flag detected. Starting re-embedding process...");
        
        try {
            reEmbeddingService.reEmbedAllDocuments();
            logger.info("Re-embedding process completed successfully. Shutting down application.");
        } catch (Exception e) {
            logger.error("Re-embedding process failed: {}", e.getMessage(), e);
            logger.error("Application will shut down with error code 1");
            System.exit(1);
        }
        
        // Gracefully shut down the application after re-embedding
        logger.info("Initiating graceful shutdown...");
        applicationContext.close();
    }
}
