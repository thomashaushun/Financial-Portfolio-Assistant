package com.tsh11.fypcode.repository;

import com.tsh11.fypcode.domain.asset.AssetProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetProfileRepository extends JpaRepository<AssetProfile, UUID> {
    List<AssetProfile> findByUserIdOrderBySymbolAsc(UUID userId);
    Optional<AssetProfile> findByIdAndUserId(UUID id, UUID userId);
    Optional<AssetProfile> findByUserIdAndSymbolIgnoreCase(UUID userId, String symbol);
    boolean existsByUserIdAndSymbolIgnoreCase(UUID userId, String symbol);
}