package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.investor.IncomeStabilityLevel;
import com.tsh11.fypcode.domain.investor.InvestmentExperienceLevel;
import com.tsh11.fypcode.domain.investor.InvestmentGoal;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.domain.investor.RiskToleranceLevel;
import com.tsh11.fypcode.dto.request.InvestorProfileRequest;
import org.springframework.stereotype.Service;

//Calculates the user’s risk profile from their questionnaire answers.
//input: questionnaire answers
//output: system’s risk categories.
/*
logic:
Long horizon + high risk tolerance + stable income = higher score
Short horizon + low risk tolerance + unstable income = lower score
*/
/*
based on calculated score:
Low score = Conservative
Medium score = Balanced
High score = Growth
Very high score = Aggressive
 */
//This turns the system to a more active, personalized approach
//Result is passed to TargetAllocationService

@Service
public class RiskAssessmentService {

    //calculate risk score, sum of result from support function
    //range 0 - 100
    public int calculateRiskScore(InvestorProfileRequest request) {
        //prevent the system from calculating risk using missing input
        if (request == null) {
            throw new IllegalArgumentException("Investor profile request must not be null.");
        }

        int score = 0;
        score += ageScore(request.getAge());
        score += horizonScore(request.getInvestmentHorizonYears());
        score += toleranceScore(request.getRiskTolerance());
        score += experienceScore(request.getInvestmentExperience());
        score += incomeStabilityScore(request.getIncomeStability());
        score += goalScore(request.getInvestmentGoal());

        return Math.max(0, Math.min(100, score));
    }

    //classify RiskProfileType base on score
    /*
    0–30    = CONSERVATIVE
    31–55   = BALANCED
    56–80   = GROWTH
    81–100+ = AGGRESSIVE
    */
    public RiskProfileType classify(int riskScore) {
        if (riskScore <= 30) {
            return RiskProfileType.CONSERVATIVE;
        }
        if (riskScore <= 55) {
            return RiskProfileType.BALANCED;
        }
        if (riskScore <= 80) {
            return RiskProfileType.GROWTH;
        }
        return RiskProfileType.AGGRESSIVE;
    }

    //calls calculateRiskScore(request) & classify(score) in one function
    public RiskProfileType classify(InvestorProfileRequest request) {
        return classify(calculateRiskScore(request));
    }

    //score user's age
    //older = low score, younger = high score
    //reason:
    //Younger investors usually have a longer time horizon and more time to recover from market volatility.
    //Older investors receive fewer risk points because they may have less time to recover from losses.
    private int ageScore(int age) {
        if (age >= 60) {
            return 4;
        }
        if (age >= 45) {
            return 8;
        }
        if (age >= 30) {
            return 12;
        }
        return 16;
    }

    //score user investment horizon
    //logic: longer horizon gets more points
    //A longer investment horizon means the user may be able to tolerate more short-term volatility.
    //A short horizon means the user may need the money soon, so the system scores them more conservatively.
    private int horizonScore(int years) {
        if (years <= 2) {
            return 3;
        }
        if (years <= 5) {
            return 8;
        }
        if (years <= 10) {
            return 14;
        }
        return 20;
    }

    //scores the user’s stated risk tolerance.
    // higher risk tolerance higher score
    private int toleranceScore(RiskToleranceLevel tolerance) {
        if (tolerance == null) {
            return 0;
        }
        return switch (tolerance) {
            case LOW -> 4;
            case MEDIUM -> 12;
            case HIGH -> 20;
            case VERY_HIGH -> 28;
        };
    }

    //scores the user’s investment experience.
    // more experience, higher score
    // low experience, low score
    private int experienceScore(InvestmentExperienceLevel experience) {
        if (experience == null) {
            return 0;
        }
        return switch (experience) {
            case BEGINNER -> 3;
            case INTERMEDIATE -> 8;
            case ADVANCED -> 12;
        };
    }

    //scores how stable the user’s income is
    //high income stability, high score
    //low income stability, low score
    private int incomeStabilityScore(IncomeStabilityLevel stability) {
        if (stability == null) {
            return 0;
        }
        return switch (stability) {
            case LOW -> 2;
            case MEDIUM -> 6;
            case HIGH -> 10;
        };
    }

    //scores the user’s investment objective
    //logic:
    //Capital preservation → lower risk
    //Balanced growth      → medium risk
    //retirement           → med-high risk
    //long term            → higher risk
    private int goalScore(InvestmentGoal goal) {
        if (goal == null) {
            return 0;
        }
        return switch (goal) {
            case CAPITAL_PRESERVATION -> 2;
            case BALANCED_GROWTH -> 6;
            case RETIREMENT -> 9;
            case LONG_TERM_GROWTH -> 12;
        };
    }
}
