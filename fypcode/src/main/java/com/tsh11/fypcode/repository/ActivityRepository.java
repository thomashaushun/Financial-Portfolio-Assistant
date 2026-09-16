package com.tsh11.fypcode.repository;

import com.tsh11.fypcode.domain.activity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByUserIdOrderByDateDesc(UUID userId);
    Optional<Activity> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}