package com.github.guoyyit.phylogenetictreejava.config;

import com.github.guoyyit.phylogenetictreejava.service.TaskManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Wires the local analysis service. All storage stays under the configured
 * local work directory; no remote services are used.
 */
@Configuration
public class ServiceConfig {

    @Value("${phylo.work-dir:${java.io.tmpdir}/phylo-work}")
    private String workDir;

    @Value("${phylo.concurrency:4}")
    private int concurrency;

    @Bean
    public TaskManager taskManager() {
        return new TaskManager(Path.of(workDir), concurrency);
    }
}
