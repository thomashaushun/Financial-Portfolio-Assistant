package com.tsh11.fypcode.domain.investor;

//categories used by the assistant when analysing the actual portfolio.
//all categories are existing categories defined in holding

//The assistant needs to map these into analysis buckets,
// so it can compare traditional allocation categories while still highlighting alternative assets.

// The target model focuses on equity, bond, and cash allocation,
// but the assistant also handles crypto, real estate, other, and unclassified
// so that other non-traditional holdings are not hidden.
public enum AssistantAllocationBucket {
    EQUITY,
    BOND,
    CASH,
    CRYPTO,
    REAL_ESTATE,
    OTHER,
    UNCLASSIFIED
}
