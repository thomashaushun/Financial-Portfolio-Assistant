package com.tsh11.fypcode.dto.response;

public class BenchmarkSearchResultResponse {

    private String symbol;
    private String name;

    public BenchmarkSearchResultResponse() {
    }

    public BenchmarkSearchResultResponse(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}