package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.domain.investor.IncomeStabilityLevel;
import com.tsh11.fypcode.domain.investor.InvestmentExperienceLevel;
import com.tsh11.fypcode.domain.investor.InvestmentGoal;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.domain.investor.RiskToleranceLevel;

import java.time.Instant;
import java.util.UUID;

//the investor profile returned to the frontend.
//contains the user’s saved profile plus the calculated risk profile.
//This lets the frontend display the saved questionnaire data and the system-derived risk profile.
//sample output:
//Age: 24
//Investment horizon: 10 years
//Risk tolerance: HIGH
//Experience: BEGINNER
//Risk profile: GROWTH

public class InvestorProfileResponse {
    private UUID id;
    private int age;
    private int investmentHorizonYears;
    private RiskToleranceLevel riskTolerance;
    private InvestmentExperienceLevel investmentExperience;
    private IncomeStabilityLevel incomeStability;
    private InvestmentGoal investmentGoal;
    private RiskProfileType riskProfileType;
    private int riskScore;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
