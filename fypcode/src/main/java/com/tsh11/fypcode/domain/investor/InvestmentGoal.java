package com.tsh11.fypcode.domain.investor;

//Represents the user’s investment objective.
// This is one of the questionnaire inputs used by RiskAssessmentService.

//parameters in existing service:
//CAPITAL_PRESERVATION
//BALANCED_GROWTH
//LONG_TERM_GROWTH
//RETIREMENT

public enum InvestmentGoal {
    CAPITAL_PRESERVATION,
    BALANCED_GROWTH,
    LONG_TERM_GROWTH,
    RETIREMENT
}
