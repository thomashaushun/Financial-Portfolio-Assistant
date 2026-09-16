package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

//input: assessment result from RiskAssessmentService, a.k.a. risk profile
//output: the target allocation for a given risk profile.
/*
Reason for this choice:
Other people do it?
 */
/*
Risk profile: BALANCED
Target allocation: 60% equity, 30% bond, 10% cash
 */

@Service
public class TargetAllocationService {

    //main method
    public TargetAllocationResponse getTargetAllocation(RiskProfileType profileType) {
        if (profileType == null) {
            throw new IllegalArgumentException("Risk profile type must not be null.");
        }

        return switch (profileType) {
            case CONSERVATIVE -> target(profileType, "40", "50", "10");
            case BALANCED -> target(profileType, "60", "30", "10");
            case GROWTH -> target(profileType, "75", "20", "5");
            case AGGRESSIVE -> target(profileType, "90", "10", "0");
        };
    }

    //helper
    //convert values to BigDecimal, and returns a structured response object.
    private TargetAllocationResponse target(RiskProfileType profileType,
                                            String equity,
                                            String bond,
                                            String cash) {
        TargetAllocationResponse response = new TargetAllocationResponse();
        response.setRiskProfileType(profileType);
        response.setEquityPercent(new BigDecimal(equity));
        response.setBondPercent(new BigDecimal(bond));
        response.setCashPercent(new BigDecimal(cash));
        return response;
    }
}
