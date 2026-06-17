package com.paicore.file_loader_service.service;

import com.paicore.file_loader_service.config.FileLoaderProperties;
import com.paicore.file_loader_service.entity.CallDetailRecord;
import com.paicore.file_loader_service.entity.CdrLog;
import com.paicore.file_loader_service.parser.CdrLineParser;
import com.paicore.file_loader_service.repository.CallDetailRecordRepository;
import com.paicore.file_loader_service.repository.CdrLogRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring-injected configuration bean")
public class CdrIngestionService {

	private final CdrLogRepository cdrLogRepository;
	private final CallDetailRecordRepository callDetailRecordRepository;
	private final CdrLineParser cdrLineParser;
	private final FileLoaderProperties fileLoaderProperties;

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public void processFile(Path filePath) {
		String fileName = resolveFileName(filePath);
		CdrLog cdrLog = CdrLog.builder()
				.fileName(fileName)
				.uploadStartTime(LocalDateTime.now())
				.successCount(0)
				.failedCount(0)
				.build();
		cdrLog = cdrLogRepository.save(cdrLog);

		int successCount = 0;
		int failedCount = 0;
		int batchSize = fileLoaderProperties.getBatchSize();
		List<CallDetailRecord> batch = new ArrayList<>(batchSize);

		try (BufferedReader reader = Files.newBufferedReader(filePath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}

				try {
					CallDetailRecord record = cdrLineParser.parse(line);
					batch.add(record);
					successCount++;

					if (batch.size() >= batchSize) {
						flushBatch(batch);
					}
				}
				catch (RuntimeException ex) {
					failedCount++;
					log.warn("Failed to parse line in file '{}': {}", fileName, ex.getMessage());
				}
			}

			if (!batch.isEmpty()) {
				flushBatch(batch);
			}
		}
		catch (IOException ex) {
			log.error("Failed to read file '{}'", fileName, ex);
			throw new IllegalStateException("Unable to read CDR file: " + fileName, ex);
		}
		finally {
			cdrLog.setUploadEndTime(LocalDateTime.now());
			cdrLog.setSuccessCount(successCount);
			cdrLog.setFailedCount(failedCount);
			cdrLogRepository.save(cdrLog);
		}

		log.info(
				"Completed ingestion for '{}': success={}, failed={}",
				fileName,
				successCount,
				failedCount);
	}

	private void flushBatch(List<CallDetailRecord> batch) {
		callDetailRecordRepository.saveAll(batch);
		entityManager.flush();
		entityManager.clear();
		batch.clear();
	}

	private static String resolveFileName(Path filePath) {
		Path fileNamePath = filePath.getFileName();
		if (fileNamePath == null) {
			throw new IllegalArgumentException("File path has no file name component: " + filePath);
		}
		return fileNamePath.toString();
	}
}
