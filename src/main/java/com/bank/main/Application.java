package com.bank.main;

import com.bank.vwap.CurrencyData;
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
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load properties file: " + e);
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
        } finally {
            // Log summary statistics for each currency pair
            System.out.println("\nSummary Statistics for Currency Pairs:");
            for (String currencyPair : CURRENCY_PAIRS) {
                CurrencyData data = calculator.getCurrencyPairData().get(currencyPair);
                if (data != null && !data.isEmpty()) {
                    double high = Double.MIN_VALUE;
                    double low = Double.MAX_VALUE;
                    for (CurrencyPriceData priceData : data.getPriceStream()) {
                        double price = priceData.getPrice();
                        high = Math.max(high, price);
                        low = Math.min(low, price);
                    }
                    double vwap = data.getVwap();
                    System.out.printf("Currency Pair: %s, High: %.4f, Low: %.4f, VWAP: %.4f%n",
                            currencyPair, high, low, vwap);
                } else {
                    System.out.printf("Currency Pair: %s, No data available%n", currencyPair);
                }
            }

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
