package com.aiarchitect.repository;

import com.aiarchitect.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.aiarchitect.entity.PhaseResultEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhaseResultRepository extends JpaRepository<PhaseResultEntity, Long> {

    List<PhaseResultEntity> findBySessionIdOrderByPhaseIdAsc(String sessionId);

    Optional<PhaseResultEntity> findBySessionIdAndPhaseId(String sessionId, int phaseId);

    @Query("SELECT p FROM PhaseResultEntity p WHERE p.sessionId = :sessionId AND p.status = 'COMPLETE'")
    List<PhaseResultEntity> findCompletedBySessionId(String sessionId);

    @Query("SELECT AVG(p.confidence) FROM PhaseResultEntity p WHERE p.sessionId = :sessionId AND p.confidence IS NOT NULL")
    Double avgConfidenceBySession(String sessionId);
}
