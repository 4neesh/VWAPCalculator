package com.bank.main;

import com.bank.vwap.CurrencyPriceData;
import com.bank.vwap.VWAPCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Application {
    private static final List<String> CURRENCY_PAIRS = List.of("AUD/USD", "USD/JPY", "NZD/GBP");
    private static final Random RANDOM = new Random();
    private static final int PRICES_PER_SECOND = 1000;
    private static final int DURATION_SECONDS = 10;

    public static void main(String[] args) {
        Properties properties = new Properties();
        try (InputStream input = Application.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) {
                System.out.println("Unable to find application.properties");
                return;
            }
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ExecutorService executor = Executors.newFixedThreadPool(3);
        VWAPCalculator calculator = new VWAPCalculator(Integer.parseInt(properties.getProperty("cutoff.seconds")));

        try {
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
        }
        finally {
            calculator.shutdownExecutors();
            executor.shutdown();
        }
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
                return 0.63 + (RANDOM.nextDouble() * 0.02);
            case "USD/JPY":
                return 150.0 + (RANDOM.nextDouble() * 5.0);
            case "NZD/GBP":
                return 0.44 + (RANDOM.nextDouble() * 0.01);
            default:
                return 1.0;
        }
    }

}
