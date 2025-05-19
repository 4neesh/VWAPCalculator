package com.bank.vwap;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VWAPCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VWAPCalculator.class);
    private Integer cutoffSeconds;
    protected static String PRICE_TIMEZONE = "Australia/Sydney";

    private final BlockingQueue<CurrencyPriceData> priceUpdateQueue = new LinkedBlockingQueue<>();
    private final Map<String, CurrencyData> currencyPairData = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService priceFeedConsumerExecutorService = Executors.newSingleThreadScheduledExecutor();

    // Track statistics for each currency pair
    private final Map<String, PriceStatistics> currencyPairStats = new ConcurrentHashMap<>();

    public VWAPCalculator(Integer cutoffSeconds){
        this.cutoffSeconds = cutoffSeconds;
        cleanupScheduledExecutor.scheduleWithFixedDelay(this::clearCutoffPricesForAllCurrencyPairs, this.cutoffSeconds, this.cutoffSeconds, TimeUnit.SECONDS);
        startConsumingPriceUpdates();
    }

    private void startConsumingPriceUpdates() {
        priceFeedConsumerExecutorService.submit(() -> {
            while (true) {
                try {
                    CurrencyPriceData update = priceUpdateQueue.take();
                    processVWAPForCurrencyPair(update);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void sendVWAPForCurrencyPair(CurrencyPriceData currencyPriceData) {
        priceUpdateQueue.offer(currencyPriceData);
    }

    protected void processVWAPForCurrencyPair(CurrencyPriceData currencyPriceData) {
        try {
            String currencyPair = currencyPriceData.getCurrencyPair();
            double price = currencyPriceData.getPrice();

            // Check if this is a new currency pair
            boolean isNewCurrencyPair = !currencyPairData.containsKey(currencyPair);

            CurrencyData data = currencyPairData.computeIfAbsent(
                    currencyPair, k -> new CurrencyData()
            );

            // Update statistics
            currencyPairStats.computeIfAbsent(currencyPair, k -> new PriceStatistics())
                    .updateStatistics(price);

            data.getPriceStream().addFirst(currencyPriceData);
            data.getTotalWeightedPrice().add(currencyPriceData.getPrice() * currencyPriceData.getVolume());
            data.getTotalVolume().addAndGet(currencyPriceData.getVolume());

            calculateVWAP(currencyPair, currencyPriceData.getTimestamp());

            // Only log when a new currency pair is added
            if (isNewCurrencyPair) {
                LOGGER.info("New currency pair added: {}", currencyPair);
            }

        } catch (Exception e) {
            LOGGER.error("Error processing price update for {}: {}", currencyPriceData.getCurrencyPair(), e.getMessage());
        }
    }

    private void calculateVWAP(String currencyPair, Instant timestamp) {
        try {
            removePricesBeforeCutoff(currencyPair, timestamp);
            CurrencyData data = currencyPairData.get(currencyPair);
            if (data != null) {
                double totalWeightedPrice = data.getTotalWeightedPrice().sum();
                long totalVolume = data.getTotalVolume().get();
                
                if (totalVolume > 0) {
                    double vwap = totalWeightedPrice / totalVolume;
                    data.setVwap(vwap);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error calculating VWAP for {}: {}", currencyPair, e.getMessage());
        }
    }


    protected void removePricesBeforeCutoff(String currencyPair, Instant timestamp) {
        if (currencyPair == null || timestamp == null) {
            LOGGER.warn("Cannot remove prices with null currencyPair or timestamp");
            return;
        }

        try {
            CurrencyData data = currencyPairData.get(currencyPair);
            if (data == null) {
                return;
            }

            Instant cutoffTime = timestamp.minusSeconds(this.cutoffSeconds);
            Deque<CurrencyPriceData> priceStream = data.getPriceStream();
            boolean pricesRemovedFromStream = false;

            // Use iterator.remove() to safely remove elements during iteration
            Iterator<CurrencyPriceData> iterator = priceStream.descendingIterator();
            while (iterator.hasNext()) {
                CurrencyPriceData priceData = iterator.next();
                if (priceData.getTimestamp().isBefore(cutoffTime)) {
                    // Subtract the price contribution before removing
                    double weightedPrice = priceData.getPrice() * priceData.getVolume();
                    data.getTotalWeightedPrice().add(-weightedPrice);
                    data.getTotalVolume().addAndGet(-priceData.getVolume());

                    // Use iterator's remove method instead of collection's remove
                    iterator.remove();
                    pricesRemovedFromStream = true;
                } else {
                    // Since data is time-ordered, we can stop once we find data within cutoff
                    break;
                }
            }

            // Cleanup currency pairs without prices within cutoff time
            if (pricesRemovedFromStream && data.getTotalVolume().get() <= 0) {
                currencyPairData.remove(currencyPair);
                LOGGER.debug("Removed currency pair {} as it has no recent price data", currencyPair);
            }
        } catch (Exception e) {
            LOGGER.error("Error during price cleanup for {}: {}", currencyPair, e.getMessage(), e);
        }
    }

    protected void clearCutoffPricesForAllCurrencyPairs(){
        Instant currentTime = Instant.now();
        for(String currencyPair: new HashSet<>(currencyPairData.keySet())){
            this.removePricesBeforeCutoff(currencyPair, currentTime);
        }
    }

    public Map<String, CurrencyData> getCurrencyPairData() {
        return currencyPairData;
    }

    private void logProcessedResult(CurrencyPriceData currencyPriceData) {
        // Removed detailed logging for each price update
    }

    public void shutdownExecutors(){
        // Log summary statistics before shutdown
        logSummaryStatistics();

        this.priceFeedConsumerExecutorService.shutdown();
        this.cleanupScheduledExecutor.shutdown();
    }

    /**
     * Logs summary statistics for all currency pairs
     */
    private void logSummaryStatistics() {
        LOGGER.info("=== Currency Pair Summary Statistics ===");
        for (Map.Entry<String, PriceStatistics> entry : currencyPairStats.entrySet()) {
            String currencyPair = entry.getKey();
            PriceStatistics stats = entry.getValue();

            LOGGER.info("{}: High: {}, Low: {}, Average: {}",
                    currencyPair,
                    String.format("%.6f", stats.getHighPrice()),
                    String.format("%.6f", stats.getLowPrice()),
                    String.format("%.6f", stats.getAveragePrice()));
        }
        LOGGER.info("======================================");
    }

    /**
     * Inner class to track price statistics for a currency pair
     */
    private static class PriceStatistics {
        private double highPrice = Double.MIN_VALUE;
        private double lowPrice = Double.MAX_VALUE;
        private double totalPrice = 0;
        private long count = 0;

        public synchronized void updateStatistics(double price) {
            if (price > highPrice) {
                highPrice = price;
            }
            if (price < lowPrice) {
                lowPrice = price;
            }
            totalPrice += price;
            count++;
        }

        public double getHighPrice() {
            return highPrice;
        }

        public double getLowPrice() {
            return lowPrice;
        }

        public double getAveragePrice() {
            return count > 0 ? totalPrice / count : 0;
        }
    }
}

