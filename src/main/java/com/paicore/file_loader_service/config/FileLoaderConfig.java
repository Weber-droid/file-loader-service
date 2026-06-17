package com.paicore.file_loader_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileLoaderProperties.class)
public class FileLoaderConfig {
}
