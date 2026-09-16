package com.tsh11.fypcode.domain.investor;

// This enum standardises the output of the risk assessment process.
// The system uses controlled risk categories.

//A user with low risk tolerance and short horizon may be classified as CONSERVATIVE.
//A user with high risk tolerance and long horizon may be classified as AGGRESSIVE.

public enum RiskProfileType {
    CONSERVATIVE,
    BALANCED,
    GROWTH,
    AGGRESSIVE
}
