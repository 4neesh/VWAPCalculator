package com.bank.main;

import com.bank.vwap.VWAPCalculator;

public class Application {

    public static void main(String[] args) {
        VWAPCalculator calculator = new VWAPCalculator();

        try{
            calculator.processVWAPForCurrencyPair("9:30 AM","AUD/USD", 0.6905, 106198);
            calculator.processVWAPForCurrencyPair("9:31 AM","USD/JPY", 142.497, 30995);
            calculator.processVWAPForCurrencyPair("9:32 AM","USD/JPY", 139.392, 2890000);
            calculator.processVWAPForCurrencyPair("9:33 AM","AUD/USD", 0.6899, 444134);
            calculator.processVWAPForCurrencyPair("9:34 AM","NZD/GBP", 0.4731, 64380);
            calculator.processVWAPForCurrencyPair("9:35 AM","NZD/GBP", 0.4725, 8226295);
        }
        finally {
            calculator.shutdown();
        }
    }
}
