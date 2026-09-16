package com.tsh11.fypcode.integration.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;

public class HistoricalPricePoint {
    private LocalDate date;
    private BigDecimal close;

    public HistoricalPricePoint(LocalDate date, BigDecimal close) {
        this.date = date;
        this.close = close;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getClose() {
        return close;
    }
}