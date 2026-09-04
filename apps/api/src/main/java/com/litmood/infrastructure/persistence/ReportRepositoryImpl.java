package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Report;
import com.litmood.domain.repository.ReportRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

interface ReportJpaRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, Report.ReportTarget targetType, Long targetId);
}

@Repository
class ReportRepositoryImpl implements ReportRepository {

    private final ReportJpaRepository jpa;

    ReportRepositoryImpl(ReportJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Report save(Report report) {
        return jpa.save(report);
    }

    @Override
    public boolean existsBy(Long reporterId, Report.ReportTarget targetType, Long targetId) {
        return jpa.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId);
    }
}
