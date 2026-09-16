package com.tsh11.fypcode.dto.response;

import java.math.BigDecimal;

//the comparison between target and actual allocation.
//It allows the frontend to show exactly where the portfolio differs from the target.
//The user can see not just the recommendation, but the calculation behind it.

//example:
//Asset bucket: EQUITY
//Target: 60%
//Actual: 75%
//Difference: +15%
//Status: OVERWEIGHT

public class AllocationComparisonResponse {
    private String bucket;
    private BigDecimal targetPercent;
    private BigDecimal actualPercent;
    private BigDecimal differencePercent;
    private String status;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public BigDecimal getTargetPercent() {
        return targetPercent;
    }

    public void setTargetPercent(BigDecimal targetPercent) {
        this.targetPercent = targetPercent;
    }

    public BigDecimal getActualPercent() {
        return actualPercent;
    }

    public void setActualPercent(BigDecimal actualPercent) {
        this.actualPercent = actualPercent;
    }

    public BigDecimal getDifferencePercent() {
        return differencePercent;
    }

    public void setDifferencePercent(BigDecimal differencePercent) {
        this.differencePercent = differencePercent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
