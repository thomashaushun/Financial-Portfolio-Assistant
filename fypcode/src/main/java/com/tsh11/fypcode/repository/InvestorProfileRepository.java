package com.tsh11.fypcode.repository;

import com.tsh11.fypcode.domain.investor.InvestorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

//Provides database access for InvestorProfile.
//allows the system to save, update, and retrieve the investor profile for the currently logged-in user.
//using userID

public interface InvestorProfileRepository extends JpaRepository<InvestorProfile, UUID> {
    Optional<InvestorProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
