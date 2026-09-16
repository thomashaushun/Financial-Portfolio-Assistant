package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.investor.InvestorProfile;
import com.tsh11.fypcode.dto.request.InvestorProfileRequest;
import com.tsh11.fypcode.dto.response.InvestorProfileResponse;
import com.tsh11.fypcode.repository.InvestorProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

//Handles creating, updating, retrieving, and converting investor profiles.
/*
User submits profile form
↓
InvestorProfileService validates/saves profile
↓
RiskAssessmentService calculates risk profile
↓
InvestorProfileRepository stores the result
↓
InvestorProfileResponse is returned
 */
@Service
@Transactional
public class InvestorProfileService {

    private final InvestorProfileRepository investorProfileRepository;
    private final RiskAssessmentService riskAssessmentService;

    public InvestorProfileService(InvestorProfileRepository investorProfileRepository,
                                  RiskAssessmentService riskAssessmentService) {
        this.investorProfileRepository = investorProfileRepository;
        this.riskAssessmentService = riskAssessmentService;
    }

    @Transactional(readOnly = true)
    public Optional<InvestorProfileResponse> getForUser(UUID userId) {
        validateUserId(userId);
        return investorProfileRepository.findByUserId(userId).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<InvestorProfile> findEntityForUser(UUID userId) {
        validateUserId(userId);
        return investorProfileRepository.findByUserId(userId);
    }

    public InvestorProfileResponse createOrUpdate(UUID userId, InvestorProfileRequest request) {
        validateUserId(userId);
        validateRequest(request);

        InvestorProfile profile = investorProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    InvestorProfile created = new InvestorProfile();
                    created.setUserId(userId);
                    return created;
                });

        applyRequest(profile, request);
        InvestorProfile saved = investorProfileRepository.save(profile);
        return toResponse(saved);
    }

    public InvestorProfileResponse update(UUID userId, InvestorProfileRequest request) {
        validateUserId(userId);
        validateRequest(request);

        InvestorProfile profile = investorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Investor profile has not been created yet."));

        applyRequest(profile, request);
        InvestorProfile saved = investorProfileRepository.save(profile);
        return toResponse(saved);
    }

    private void applyRequest(InvestorProfile profile, InvestorProfileRequest request) {
        //This is where questionnaire answers become a numeric risk score.
        int riskScore = riskAssessmentService.calculateRiskScore(request);

        profile.setAge(request.getAge());
        profile.setInvestmentHorizonYears(request.getInvestmentHorizonYears());
        profile.setRiskTolerance(request.getRiskTolerance());
        profile.setInvestmentExperience(request.getInvestmentExperience());
        profile.setIncomeStability(request.getIncomeStability());
        profile.setInvestmentGoal(request.getInvestmentGoal());
        profile.setRiskScore(riskScore);
        //This turns the numeric score into a user profile risk type
        profile.setRiskProfileType(riskAssessmentService.classify(riskScore));
    }

    private InvestorProfileResponse toResponse(InvestorProfile profile) {
        InvestorProfileResponse response = new InvestorProfileResponse();
        response.setId(profile.getId());
        response.setAge(profile.getAge());
        response.setInvestmentHorizonYears(profile.getInvestmentHorizonYears());
        response.setRiskTolerance(profile.getRiskTolerance());
        response.setInvestmentExperience(profile.getInvestmentExperience());
        response.setIncomeStability(profile.getIncomeStability());
        response.setInvestmentGoal(profile.getInvestmentGoal());
        response.setRiskScore(profile.getRiskScore());
        response.setRiskProfileType(profile.getRiskProfileType());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        return response;
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }
    }

    private void validateRequest(InvestorProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Investor profile request must not be null.");
        }
    }
}
