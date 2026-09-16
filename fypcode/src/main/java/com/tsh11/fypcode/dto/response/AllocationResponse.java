package com.tsh11.fypcode.dto.response;

import java.util.List;

public class AllocationResponse {

    private List<AllocationItemResponse> byHolding;
    private List<AllocationItemResponse> byAssetClass;
    private List<AllocationItemResponse> bySector;
    private List<AllocationItemResponse> byMarket;
    private List<AllocationItemResponse> byCurrency;

    public List<AllocationItemResponse> getByHolding() {
        return byHolding;
    }

    public void setByHolding(List<AllocationItemResponse> byHolding) {
        this.byHolding = byHolding;
    }

    public List<AllocationItemResponse> getByAssetClass() {
        return byAssetClass;
    }

    public void setByAssetClass(List<AllocationItemResponse> byAssetClass) {
        this.byAssetClass = byAssetClass;
    }

    public List<AllocationItemResponse> getBySector() {
        return bySector;
    }

    public void setBySector(List<AllocationItemResponse> bySector) {
        this.bySector = bySector;
    }

    public List<AllocationItemResponse> getByMarket() {
        return byMarket;
    }

    public void setByMarket(List<AllocationItemResponse> byMarket) {
        this.byMarket = byMarket;
    }

    public List<AllocationItemResponse> getByCurrency() {
        return byCurrency;
    }

    public void setByCurrency(List<AllocationItemResponse> byCurrency) {
        this.byCurrency = byCurrency;
    }
}