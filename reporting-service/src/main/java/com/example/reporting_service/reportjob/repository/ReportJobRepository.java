package com.example.reporting_service.reportjob.repository;

import com.example.reporting_service.reportjob.entity.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {
}
