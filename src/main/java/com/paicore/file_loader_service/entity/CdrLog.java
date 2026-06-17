package com.paicore.file_loader_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cdr_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CdrLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "file_name")
	private String fileName;

	@Column(name = "upload_start_time")
	private LocalDateTime uploadStartTime;

	@Column(name = "upload_end_time")
	private LocalDateTime uploadEndTime;

	@Column(name = "success_count")
	private Integer successCount;

	@Column(name = "failed_count")
	private Integer failedCount;
}
