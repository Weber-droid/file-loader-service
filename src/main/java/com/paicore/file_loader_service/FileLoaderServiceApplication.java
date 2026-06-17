package com.paicore.file_loader_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FileLoaderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FileLoaderServiceApplication.class, args);
	}

}
