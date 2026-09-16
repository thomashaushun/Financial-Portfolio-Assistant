package com.tsh11.fypcode.domain.investor;

// Represents the user’s investing experience.
// This is one of the questionnaire inputs used by RiskAssessmentService.

// helps the system distinguish between users who may need more cautious guidance and users who understand investment risk better.

public enum InvestmentExperienceLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
