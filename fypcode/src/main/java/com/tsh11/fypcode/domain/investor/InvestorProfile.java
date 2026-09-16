package com.tsh11.fypcode.domain.investor;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

// Represents the user’s saved investor profile in the database.

// This is the source of truth for the user’s personal risk profile.
// The assistant uses this profile to decide what target allocation is suitable for the user.

// Existing portfolio features are based on activities and holdings, but the assistant needs user-specific context.
// This file provides that context.
@Entity
@Table(
        name = "investor_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_investor_profile_user", columnNames = {"user_id"})
        }
)
public class InvestorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int age;

    @Column(name = "investment_horizon_years", nullable = false)
    private int investmentHorizonYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tolerance", nullable = false, length = 40)
    private RiskToleranceLevel riskTolerance;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_experience", nullable = false, length = 40)
    private InvestmentExperienceLevel investmentExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_stability", nullable = false, length = 40)
    private IncomeStabilityLevel incomeStability;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_goal", nullable = false, length = 40)
    private InvestmentGoal investmentGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile_type", nullable = false, length = 40)
    private RiskProfileType riskProfileType;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getInvestmentHorizonYears() {
        return investmentHorizonYears;
    }

    public void setInvestmentHorizonYears(int investmentHorizonYears) {
        this.investmentHorizonYears = investmentHorizonYears;
    }

    public RiskToleranceLevel getRiskTolerance() {
        return riskTolerance;
    }

    public void setRiskTolerance(RiskToleranceLevel riskTolerance) {
        this.riskTolerance = riskTolerance;
    }

    public InvestmentExperienceLevel getInvestmentExperience() {
        return investmentExperience;
    }

    public void setInvestmentExperience(InvestmentExperienceLevel investmentExperience) {
        this.investmentExperience = investmentExperience;
    }

    public IncomeStabilityLevel getIncomeStability() {
        return incomeStability;
    }

    public void setIncomeStability(IncomeStabilityLevel incomeStability) {
        this.incomeStability = incomeStability;
    }

    public InvestmentGoal getInvestmentGoal() {
        return investmentGoal;
    }

    public void setInvestmentGoal(InvestmentGoal investmentGoal) {
        this.investmentGoal = investmentGoal;
    }

    public RiskProfileType getRiskProfileType() {
        return riskProfileType;
    }

    public void setRiskProfileType(RiskProfileType riskProfileType) {
        this.riskProfileType = riskProfileType;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
