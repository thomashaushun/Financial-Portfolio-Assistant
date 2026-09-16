package com.tsh11.fypcode.service.assistant.whitebox;

import com.tsh11.fypcode.domain.investor.*;
import com.tsh11.fypcode.dto.request.InvestorProfileRequest;
import com.tsh11.fypcode.dto.response.InvestorProfileResponse;
import com.tsh11.fypcode.repository.InvestorProfileRepository;
import com.tsh11.fypcode.service.assistant.InvestorProfileService;
import com.tsh11.fypcode.service.assistant.RiskAssessmentService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvestorProfileServiceBranchTest {

    private final InvestorProfileRepository investorProfileRepository =
            mock(InvestorProfileRepository.class);

    private final RiskAssessmentService riskAssessmentService =
            mock(RiskAssessmentService.class);

    private final InvestorProfileService service =
            new InvestorProfileService(investorProfileRepository, riskAssessmentService);

    // Tests the branch where a profile exists and is converted into InvestorProfileResponse.
    @Test
    @DisplayName("getForUser returns profile response when profile exists")
    void getForUserReturnsProfileWhenProfileExists() {
        UUID userId = UUID.randomUUID();
        InvestorProfile profile = existingProfile(userId);

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(profile));

        Optional<InvestorProfileResponse> result = service.getForUser(userId);

        assertTrue(result.isPresent());
        assertEquals(profile.getAge(), result.get().getAge());
        assertEquals(profile.getRiskProfileType(), result.get().getRiskProfileType());

        verify(investorProfileRepository).findByUserId(userId);
    }

    // Tests the branch where no profile exists for the user.
    @Test
    @DisplayName("getForUser returns empty when profile does not exist")
    void getForUserReturnsEmptyWhenProfileDoesNotExist() {
        UUID userId = UUID.randomUUID();

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        Optional<InvestorProfileResponse> result = service.getForUser(userId);

        assertTrue(result.isEmpty());
        verify(investorProfileRepository).findByUserId(userId);
    }

    // Tests direct retrieval of the entity for internal service use.
    @Test
    @DisplayName("findEntityForUser returns profile entity when profile exists")
    void findEntityForUserReturnsEntityWhenProfileExists() {
        UUID userId = UUID.randomUUID();
        InvestorProfile profile = existingProfile(userId);

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(profile));

        Optional<InvestorProfile> result = service.findEntityForUser(userId);

        assertTrue(result.isPresent());
        assertSame(profile, result.get());

        verify(investorProfileRepository).findByUserId(userId);
    }

    // Tests the create branch inside createOrUpdate, including risk score calculation and profile classification.
    @Test
    @DisplayName("createOrUpdate creates a new profile when no profile exists")
    void createOrUpdateCreatesNewProfileWhenNoneExists() {
        UUID userId = UUID.randomUUID();
        InvestorProfileRequest request = validRequest();

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.empty());
        when(riskAssessmentService.calculateRiskScore(request))
                .thenReturn(55);
        when(riskAssessmentService.classify(55))
                .thenReturn(RiskProfileType.BALANCED);
        when(investorProfileRepository.save(any(InvestorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvestorProfileResponse result = service.createOrUpdate(userId, request);

        assertEquals(request.getAge(), result.getAge());
        assertEquals(request.getInvestmentHorizonYears(), result.getInvestmentHorizonYears());
        assertEquals(RiskProfileType.BALANCED, result.getRiskProfileType());
        assertEquals(55, result.getRiskScore());

        verify(investorProfileRepository).findByUserId(userId);
        verify(investorProfileRepository).save(any(InvestorProfile.class));
    }

    // Tests the update-existing branch inside createOrUpdate.
    @Test
    @DisplayName("createOrUpdate updates an existing profile when profile already exists")
    void createOrUpdateUpdatesExistingProfileWhenProfileExists() {
        UUID userId = UUID.randomUUID();
        InvestorProfile existing = existingProfile(userId);
        InvestorProfileRequest request = validRequest();
        request.setAge(40);

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing));
        when(riskAssessmentService.calculateRiskScore(request))
                .thenReturn(70);
        when(riskAssessmentService.classify(70))
                .thenReturn(RiskProfileType.GROWTH);
        when(investorProfileRepository.save(any(InvestorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvestorProfileResponse result = service.createOrUpdate(userId, request);

        assertEquals(40, result.getAge());
        assertEquals(70, result.getRiskScore());
        assertEquals(RiskProfileType.GROWTH, result.getRiskProfileType());

        verify(investorProfileRepository).findByUserId(userId);
        verify(investorProfileRepository).save(existing);
    }

    // Tests the normal update path when the profile already exists.
    @Test
    @DisplayName("update updates existing profile")
    void updateUpdatesExistingProfile() {
        UUID userId = UUID.randomUUID();
        InvestorProfile existing = existingProfile(userId);
        InvestorProfileRequest request = validRequest();

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(existing));
        when(riskAssessmentService.calculateRiskScore(request))
                .thenReturn(35);
        when(riskAssessmentService.classify(35))
                .thenReturn(RiskProfileType.CONSERVATIVE);
        when(investorProfileRepository.save(any(InvestorProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvestorProfileResponse result = service.update(userId, request);

        assertEquals(35, result.getRiskScore());
        assertEquals(RiskProfileType.CONSERVATIVE, result.getRiskProfileType());

        verify(investorProfileRepository).save(existing);
    }

    // Tests the failure branch where update is called before a profile has been created.
    @Test
    @DisplayName("update throws EntityNotFoundException when profile does not exist")
    void updateThrowsWhenProfileDoesNotExist() {
        UUID userId = UUID.randomUUID();
        InvestorProfileRequest request = validRequest();

        when(investorProfileRepository.findByUserId(userId))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.update(userId, request));

        verify(investorProfileRepository).findByUserId(userId);
        verify(investorProfileRepository, never()).save(any());
    }

    // Tests validation of null userId.
    @Test
    @DisplayName("getForUser throws IllegalArgumentException when userId is null")
    void getForUserThrowsWhenUserIdIsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.getForUser(null));

        verifyNoInteractions(investorProfileRepository);
    }

    // Tests request validation in createOrUpdate.
    @Test
    @DisplayName("createOrUpdate throws IllegalArgumentException when request is null")
    void createOrUpdateThrowsWhenRequestIsNull() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdate(userId, null));

        verifyNoInteractions(investorProfileRepository);
    }

    // Tests request validation in update.
    @Test
    @DisplayName("update throws IllegalArgumentException when request is null")
    void updateThrowsWhenRequestIsNull() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.update(userId, null));

        verifyNoInteractions(investorProfileRepository);
    }

    private InvestorProfileRequest validRequest() {
        InvestorProfileRequest request = new InvestorProfileRequest();
        request.setAge(30);
        request.setInvestmentHorizonYears(10);
        request.setRiskTolerance(RiskToleranceLevel.MEDIUM);
        request.setInvestmentExperience(InvestmentExperienceLevel.INTERMEDIATE);
        request.setIncomeStability(IncomeStabilityLevel.MEDIUM);
        request.setInvestmentGoal(InvestmentGoal.BALANCED_GROWTH);
        return request;
    }

    private InvestorProfile existingProfile(UUID userId) {
        InvestorProfile profile = new InvestorProfile();
        profile.setUserId(userId);
        profile.setAge(30);
        profile.setInvestmentHorizonYears(10);
        profile.setRiskTolerance(RiskToleranceLevel.MEDIUM);
        profile.setInvestmentExperience(InvestmentExperienceLevel.INTERMEDIATE);
        profile.setIncomeStability(IncomeStabilityLevel.MEDIUM);
        profile.setInvestmentGoal(InvestmentGoal.BALANCED_GROWTH);
        profile.setRiskScore(55);
        profile.setRiskProfileType(RiskProfileType.BALANCED);
        return profile;
    }
}