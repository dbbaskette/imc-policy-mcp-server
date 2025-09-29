package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rag.process", havingValue = "true")
public class RagEtlCommandLineRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(RagEtlCommandLineRunner.class);

    @Autowired
    private RagEtlService ragEtlService;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Value("${app.rag.source-dir:./local_data/source}")
    private String sourceDirectory;

    @Override
    public void run(String... args) throws Exception {
        logger.info("=== RAG ETL COMMAND LINE RUNNER STARTING ===");
        logger.info("Source directory: {}", sourceDirectory);
        
        try {
            // Process PDF files and populate vector store
            ragEtlService.processPdfFiles(sourceDirectory);
            
            logger.info("=== RAG ETL PROCESSING COMPLETED SUCCESSFULLY ===");
            logger.info("Application will now shutdown as requested");
            
        } catch (Exception e) {
            logger.error("RAG ETL processing failed: {}", e.getMessage(), e);
            logger.error("Application will shutdown with error");
            System.exit(1);
        } finally {
            // Shutdown the application after processing
            applicationContext.close();
        }
    }
}