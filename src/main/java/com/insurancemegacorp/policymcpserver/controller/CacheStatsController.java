package com.insurancemegacorp.policymcpserver.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to display cache metrics in a human-readable HTML table.
 */
@Controller
public class CacheStatsController {

    private final MeterRegistry meterRegistry;

    public CacheStatsController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/cache-stats")
    public String getCacheStats(Model model) {
        // Get cache metrics
        Counter cacheHits = meterRegistry.find("vectorstore.cache.hits").counter();
        Counter cacheMisses = meterRegistry.find("vectorstore.cache.misses").counter();
        Counter warmingSuccess = meterRegistry.find("vectorstore.cache.warming.success").counter();
        Counter warmingFailures = meterRegistry.find("vectorstore.cache.warming.failures").counter();
        Counter documentsCached = meterRegistry.find("vectorstore.documents.cached").counter();
        Counter documentsRetrieved = meterRegistry.find("vectorstore.documents.retrieved").counter();

        // Get timing metrics
        Timer cacheQueryTime = meterRegistry.find("vectorstore.query.duration").tag("operation", "cache").timer();
        Timer primaryQueryTime = meterRegistry.find("vectorstore.query.duration").tag("operation", "primary").timer();

        // Calculate cache hit rate
        double totalQueries = (cacheHits != null ? cacheHits.count() : 0) + (cacheMisses != null ? cacheMisses.count() : 0);
        double hitRate = totalQueries > 0 ? ((cacheHits != null ? cacheHits.count() : 0) / totalQueries) * 100 : 0;

        // Add metrics to model
        model.addAttribute("cacheHits", cacheHits != null ? (long)cacheHits.count() : 0);
        model.addAttribute("cacheMisses", cacheMisses != null ? (long)cacheMisses.count() : 0);
        model.addAttribute("warmingSuccess", warmingSuccess != null ? (long)warmingSuccess.count() : 0);
        model.addAttribute("warmingFailures", warmingFailures != null ? (long)warmingFailures.count() : 0);
        model.addAttribute("documentsCached", documentsCached != null ? (long)documentsCached.count() : 0);
        model.addAttribute("documentsRetrieved", documentsRetrieved != null ? (long)documentsRetrieved.count() : 0);
        model.addAttribute("hitRate", String.format("%.1f%%", hitRate));

        // Add timing metrics
        model.addAttribute("cacheQueryAvg", cacheQueryTime != null ? String.format("%.1fms", cacheQueryTime.mean(java.util.concurrent.TimeUnit.MILLISECONDS)) : "N/A");
        model.addAttribute("primaryQueryAvg", primaryQueryTime != null ? String.format("%.1fms", primaryQueryTime.mean(java.util.concurrent.TimeUnit.MILLISECONDS)) : "N/A");

        return "cache-stats";
    }
}
