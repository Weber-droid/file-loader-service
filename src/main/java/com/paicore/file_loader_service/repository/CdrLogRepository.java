package com.paicore.file_loader_service.repository;

import com.paicore.file_loader_service.entity.CdrLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CdrLogRepository extends JpaRepository<CdrLog, Long> {
}
