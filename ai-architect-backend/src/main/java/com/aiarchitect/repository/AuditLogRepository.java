package com.aiarchitect.repository;

import com.aiarchitect.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.aiarchitect.entity.AuditLogEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findBySessionIdOrderByLoggedAtDesc(String sessionId);

    List<AuditLogEntity> findByLevelAndLoggedAtAfterOrderByLoggedAtDesc(String level, Instant after);

    List<AuditLogEntity> findTop200ByOrderByLoggedAtDesc();

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLogEntity a WHERE a.loggedAt < :before")
    int deleteOlderThan(Instant before);
}
