package com.paicore.file_loader_service.service;

import com.paicore.file_loader_service.config.FileLoaderProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-injected configuration bean")
public class FileWatcherService {

	private final FileLoaderProperties fileLoaderProperties;
	private final CdrIngestionService cdrIngestionService;

	@Scheduled(fixedDelay = 60000)
	public void pollInputDirectory() {
		Path inputDirectory = Path.of(fileLoaderProperties.getInputDirectory());
		Path archiveDirectory = Path.of(fileLoaderProperties.getArchiveDirectory());

		try {
			Files.createDirectories(inputDirectory);
			Files.createDirectories(archiveDirectory);
		}
		catch (IOException ex) {
			log.error("Unable to create watcher directories", ex);
			return;
		}

		try (Stream<Path> files = Files.list(inputDirectory)) {
			files.filter(Files::isRegularFile)
					.forEach(file -> processAndArchive(file, archiveDirectory));
		}
		catch (IOException ex) {
			log.error("Failed to list input directory '{}'", inputDirectory, ex);
		}
	}

	private void processAndArchive(Path file, Path archiveDirectory) {
		String fileName = resolveFileName(file);
		log.info("Processing file '{}'", fileName);

		try {
			cdrIngestionService.processFile(file);
			Path target = archiveDirectory.resolve(fileName);
			Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
			log.info("Archived file '{}' to '{}'", fileName, target);
		}
		catch (Exception ex) {
			log.error("Failed to process file '{}'", fileName, ex);
		}
	}

	private static String resolveFileName(Path filePath) {
		Path fileNamePath = filePath.getFileName();
		if (fileNamePath == null) {
			throw new IllegalArgumentException("File path has no file name component: " + filePath);
		}
		return fileNamePath.toString();
	}
}
