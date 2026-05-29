package com.aiarchitect.repository;

import com.aiarchitect.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.aiarchitect.entity.ProjectEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    List<ProjectEntity> findTop20ByOrderByCreatedAtDesc();

    Optional<ProjectEntity> findBySessionId(String sessionId);

    @Query("SELECT p FROM ProjectEntity p WHERE p.outcome = 'success' ORDER BY p.createdAt DESC")
    List<ProjectEntity> findSuccessfulProjects(Pageable pageable);
}
