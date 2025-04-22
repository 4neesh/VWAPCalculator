package com.bank.vwap;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class CurrencyData {
    private Double vwap;
    private final Deque<CurrencyPriceData> priceStream;
    private final DoubleAdder totalWeightedPrice;
    private final AtomicLong totalVolume;

    public CurrencyData() {
        this.priceStream = new ConcurrentLinkedDeque<>();
        this.totalWeightedPrice = new DoubleAdder();
        this.totalVolume = new AtomicLong(0);
    }

    public Double getVwap() {
        return vwap;
    }

    public void setVwap(Double vwap) {
        this.vwap = vwap;
    }

    public Deque<CurrencyPriceData> getPriceStream() {
        return priceStream;
    }

    public DoubleAdder getTotalWeightedPrice() {
        return totalWeightedPrice;
    }

    public AtomicLong getTotalVolume() {
        return totalVolume;
    }
}