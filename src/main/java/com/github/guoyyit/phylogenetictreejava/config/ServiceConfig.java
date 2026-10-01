package com.github.guoyyit.phylogenetictreejava.config;

import com.github.guoyyit.phylogenetictreejava.service.AnalysisService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;

@Configuration
public class ServiceConfig {

    @Value("${phylo.work-dir:${java.io.tmpdir}/phylo-tasks}")
    private String workDir;

    @Value("${phylo.default-concurrency:2}")
    private int defaultConcurrency;

    @Bean
    public AnalysisService analysisService() {
        return new AnalysisService(Path.of(workDir), defaultConcurrency);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        analysisService().recoverInterruptedTasks();
    }
}
