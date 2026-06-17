package com.paicore.file_loader_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "file-loader")
public class FileLoaderProperties {

	private String inputDirectory = "./data/input";

	private String archiveDirectory = "./data/input/archive";

	private int batchSize = 500;
}
