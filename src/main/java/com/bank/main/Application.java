package com.bank.main;

import com.bank.vwap.CurrencyPriceData;
import com.bank.vwap.VWAPCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Application {
    private static final List<String> CURRENCY_PAIRS = List.of("AUD/USD", "USD/JPY", "NZD/GBP");
    private static final Random RANDOM = new Random();
    private static final int PRICES_PER_SECOND = 1000;
    private static final int DURATION_SECONDS = 10;

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        VWAPCalculator calculator = new VWAPCalculator();

        for (int i = 0; i < DURATION_SECONDS; i++) {
            executor.submit(() -> {
                for (int j = 0; j < PRICES_PER_SECOND; j++) {
                    CurrencyPriceData priceData = generateRandomPriceData();
                    calculator.sendVWAPForCurrencyPair(priceData);
                }
            });
            try {
                Thread.sleep(1000); // Ensure 1000 updates per second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("COMPLETE");
        executor.shutdown();
        System.exit(0);
    }

    private static CurrencyPriceData generateRandomPriceData() {
        String currencyPair = CURRENCY_PAIRS.get(RANDOM.nextInt(CURRENCY_PAIRS.size()));
        double price = getRandomPrice(currencyPair);
        int volume = RANDOM.nextInt(1_000_000) + 1; // Random volume between 1 and 1M
        return new CurrencyPriceData(Instant.now(), currencyPair, price, volume);
    }

    private static double getRandomPrice(String currencyPair) {
        switch (currencyPair) {
            case "AUD/USD":
                return 0.68 + (RANDOM.nextDouble() * 0.02);
            case "USD/JPY":
                return 138.0 + (RANDOM.nextDouble() * 5.0);
            case "NZD/GBP":
                return 0.47 + (RANDOM.nextDouble() * 0.01);
            default:
                return 1.0;
        }
    }

}
