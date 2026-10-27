package com.devpilot.backend.repositories;

import com.devpilot.backend.entities.ConnectedRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectedRepositoryRepository extends JpaRepository<ConnectedRepository, UUID> {
    List<ConnectedRepository> findByUserIdOrderByFullNameAsc(UUID userId);

    Optional<ConnectedRepository> findByIdAndUserId(UUID id, UUID userId);

    Optional<ConnectedRepository> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
}
