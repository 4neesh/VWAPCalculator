package com.bank.vwap;

public class Application {

    public static void main(String[] args) {

        VWAPCalculator calculator = new VWAPCalculator();
        try {
            calculator.processPriceUpdate("AUD/USD", 0.6905, 106198);
            calculator.processPriceUpdate("USD/JPY", 142.497, 30995);
            calculator.processPriceUpdate("USD/JPY", 139.392, 2890000);
            calculator.processPriceUpdate("AUD/USD", 0.6899, 444134);
            calculator.processPriceUpdate("NZD/GBP", 0.4731, 64380);
            calculator.processPriceUpdate("NZD/GBP", 0.4725, 8226295);
        }
        finally {            //close down ExecutorServices
            calculator.shutdown();
        }
    }
}
