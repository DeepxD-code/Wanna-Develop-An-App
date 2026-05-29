package com.aiarchitect.repository;

import com.aiarchitect.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.aiarchitect.entity.SessionEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    List<SessionEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<SessionEntity> findTop20ByOrderByCreatedAtDesc();

    @Query("SELECT s FROM SessionEntity s WHERE s.status IN ('RUNNING','PENDING') ORDER BY s.createdAt DESC")
    List<SessionEntity> findActiveSessions();

    @Modifying
    @Transactional
    @Query("UPDATE SessionEntity s SET s.status = 'ERROR' WHERE s.status = 'RUNNING' AND s.updatedAt < :cutoff")
    int markStaleSessions(Instant cutoff);
}
