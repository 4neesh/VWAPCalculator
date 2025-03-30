package com.bank.vwap;

import java.time.Instant;

public class CurrencyPriceData {
    private Instant timestamp;
    private String currencyPair;
    private double price;
    private long volume;

    public CurrencyPriceData(Instant timestamp, String currencyPair, double price, long volume) {
        this.timestamp = timestamp;
        this.currencyPair = currencyPair;
        this.price = price;
        this.volume = volume;
    }

    public double getPrice() {
        return price;
    }

    public long getVolume() {
        return volume;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }
}
