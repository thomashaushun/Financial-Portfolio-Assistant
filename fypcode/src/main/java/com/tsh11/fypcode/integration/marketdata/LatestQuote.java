package com.tsh11.fypcode.integration.marketdata;

import java.math.BigDecimal;

public class LatestQuote {
    private final String symbol;
    private final BigDecimal price;

    public LatestQuote(String symbol, BigDecimal price) {
        this.symbol = symbol;
        this.price = price;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }
}