package com.aiarchitect.repository;

import com.aiarchitect.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.aiarchitect.entity.ProviderMetricEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface ProviderMetricRepository extends JpaRepository<ProviderMetricEntity, Long> {

    @Query("""
        SELECT p.provider, COUNT(p), SUM(CASE WHEN p.success = true THEN 1 ELSE 0 END),
               AVG(p.latencyMs), SUM(p.tokens)
        FROM ProviderMetricEntity p
        WHERE p.recordedAt > :since
        GROUP BY p.provider
        """)
    List<Object[]> aggregateByProvider(Instant since);

    @Query("""
        SELECT p.provider, p.phaseId, AVG(CASE WHEN p.success THEN 1.0 ELSE 0.0 END) as successRate
        FROM ProviderMetricEntity p
        GROUP BY p.provider, p.phaseId
        ORDER BY p.phaseId, successRate DESC
        """)
    List<Object[]> successRateByProviderAndPhase();

    List<ProviderMetricEntity> findTop100ByOrderByRecordedAtDesc();
}
