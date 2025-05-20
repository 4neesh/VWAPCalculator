package com.bank.util;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class PriceStatistics {
    private double highPrice = Double.MIN_VALUE;
    private double lowPrice = Double.MAX_VALUE;
    private DoubleAdder totalPrice = new DoubleAdder();
    private LongAdder count = new LongAdder();

    public synchronized void updateStatistics(double price) {
        if (price > highPrice) {
            highPrice = price;
        }
        if (price < lowPrice) {
            lowPrice = price;
        }
        totalPrice.add(price);
        count.add(1);    }

    public double getHighPrice() {
        return highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public double getAveragePrice() {
        return count.intValue() > 0 ? totalPrice.doubleValue() / count.intValue() : 0;
    }

}