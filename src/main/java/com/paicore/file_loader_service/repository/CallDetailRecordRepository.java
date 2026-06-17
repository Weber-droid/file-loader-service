package com.paicore.file_loader_service.repository;

import com.paicore.file_loader_service.entity.CallDetailRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallDetailRecordRepository extends JpaRepository<CallDetailRecord, String> {
}
