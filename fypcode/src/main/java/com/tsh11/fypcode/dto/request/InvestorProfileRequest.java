package com.tsh11.fypcode.dto.request;

import com.tsh11.fypcode.domain.investor.IncomeStabilityLevel;
import com.tsh11.fypcode.domain.investor.InvestmentExperienceLevel;
import com.tsh11.fypcode.domain.investor.InvestmentGoal;
import com.tsh11.fypcode.domain.investor.RiskToleranceLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

//the data sent from the frontend when the user creates or updates their investor profile.
//This file represents the questionnaire answers entered by the user.

public class InvestorProfileRequest {

    @Min(value = 18, message = "Age must be at least 18")
    @Max(value = 116, message = "Age must be realistic") //oldest person 116 yr Ethel Caterham
    private int age;

    @Min(value = 1, message = "Investment horizon must be at least 1 year")
    @Max(value = 60, message = "Investment horizon must be realistic")
    private int investmentHorizonYears;

    @NotNull
    private RiskToleranceLevel riskTolerance;

    @NotNull
    private InvestmentExperienceLevel investmentExperience;

    @NotNull
    private IncomeStabilityLevel incomeStability;

    @NotNull
    private InvestmentGoal investmentGoal;

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
}
