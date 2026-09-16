package com.tsh11.fypcode.service.portfolio;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;

public interface PortfolioOptimisationAlgorithm {

    PortfolioAlgorithm getAlgorithmType();

    PortfolioOptimisationResult optimise(PortfolioOptimisationRequest request);
}