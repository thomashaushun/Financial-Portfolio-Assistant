package com.tsh11.fypcode.domain.investor;

//Represents how stable the user’s income is.
// This is one of the questionnaire inputs used by RiskAssessmentService.

//used as part of the risk assessment.
// A user with unstable income is classified more conservative.

public enum IncomeStabilityLevel {
    LOW,
    MEDIUM,
    HIGH
}
