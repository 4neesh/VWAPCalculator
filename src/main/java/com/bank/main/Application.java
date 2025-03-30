package com.bank.main;

import com.bank.vwap.VWAPCalculator;

public class Application {

    public static void main(String[] args) {

        VWAPCalculator calculator = new VWAPCalculator();
        calculator.processPriceUpdate("9:30 AM","AUD/USD", 0.6905, 106198);
        calculator.processPriceUpdate("9:31 AM","USD/JPY", 142.497, 30995);
        calculator.processPriceUpdate("9:32 AM","USD/JPY", 139.392, 2890000);
        calculator.processPriceUpdate("9:33 AM","AUD/USD", 0.6899, 444134);
        calculator.processPriceUpdate("9:34 AM","NZD/GBP", 0.4731, 64380);
        calculator.processPriceUpdate("9:35 AM","NZD/GBP", 0.4725, 8226295);
    }
}
