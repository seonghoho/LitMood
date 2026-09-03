package com.litmood.domain.repository;

import com.litmood.domain.model.Report;

public interface ReportRepository {

    Report save(Report report);

    boolean existsBy(Long reporterId, Report.ReportTarget targetType, Long targetId);
}
