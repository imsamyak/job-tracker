package org.example.config;

import org.example.service.ShutdownService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ShutdownConfig {

    @Bean
    public ShutdownService shutdownService(ConfigurableApplicationContext context) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jtracker-shutdown");
            t.setDaemon(true);
            return t;
        });
        Runnable terminate = () -> System.exit(SpringApplication.exit(context, () -> 0));
        return new ShutdownService(scheduler, terminate);
    }

    @Bean
    public ShutdownCancelFilter shutdownCancelFilter(ShutdownService shutdownService) {
        return new ShutdownCancelFilter(shutdownService);
    }
}
