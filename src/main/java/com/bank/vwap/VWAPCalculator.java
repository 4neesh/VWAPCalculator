package com.bank.vwap;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VWAPCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger(VWAPCalculator.class);
    private static final long CLEANUP_INTERVAL_SECONDS = 1;

    private final Map<String, Double> currencyPairToVWAP = new ConcurrentHashMap<>();
    private final Map<String, Deque<CurrencyPriceData>> currencyPairToPriceStream = new ConcurrentHashMap<>();

    private final Map<String, DoubleAdder> currencyPairToTotalWeightedPrice = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> currencyPairToTotalVolume = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public VWAPCalculator() {
        scheduledExecutorService.scheduleAtFixedRate(this::removePricesBeforeCutoff, 0, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void processPriceUpdate(Instant timestamp, String currencyPair, double price, long volume) {
        try {
            CurrencyPriceData currencyPriceData = new CurrencyPriceData(timestamp, currencyPair, price, volume);
            Deque<CurrencyPriceData> currencyPairStream = currencyPairToPriceStream.computeIfAbsent(
                currencyPair, k -> new ConcurrentLinkedDeque<>()
            );
            currencyPairStream.addFirst(currencyPriceData);

            currencyPairToTotalWeightedPrice.computeIfAbsent(currencyPair, k -> new DoubleAdder())
                .add(price * volume);
            currencyPairToTotalVolume.computeIfAbsent(currencyPair, k -> new AtomicLong(0))
                .addAndGet(volume);

            calculateVWAP(currencyPair);

        } catch (Exception e) {
            LOGGER.error("Error processing price update for {}: {}", currencyPair, e.getMessage());
            throw new RuntimeException("Failed to process price update", e);
        }
    }

    private void calculateVWAP(String currencyPair) {
        try {
            double totalWeightedPrice = currencyPairToTotalWeightedPrice.get(currencyPair).sum();
            long totalVolume = currencyPairToTotalVolume.get(currencyPair).get();
            
            if (totalVolume > 0) {
                double vwap = totalWeightedPrice / totalVolume;
                currencyPairToVWAP.put(currencyPair, vwap);
                System.out.printf("Updated %s to VWAP of %s%n", currencyPair, vwap);
            }
        } catch (Exception e) {
            LOGGER.error("Error calculating VWAP for {}: {}", currencyPair, e.getMessage());
            throw e;
        }
    }

    private void removePricesBeforeCutoff() {
        try {
            Instant cutoffTime = Instant.now().minusSeconds(3600); // 1 hour

            for (Map.Entry<String, Deque<CurrencyPriceData>> entry : currencyPairToPriceStream.entrySet()) {
                String currencyPair = entry.getKey();
                Deque<CurrencyPriceData> currencyPairStream = entry.getValue();
                boolean pricesRemovedFromStream = false;

                while (currencyStreamContainsOutdatedPrices(currencyPairStream, cutoffTime)) {
                    CurrencyPriceData oldData = currencyPairStream.pollLast();
                    pricesRemovedFromStream = true;
                    
                    currencyPairToTotalWeightedPrice.get(currencyPair)
                        .add(-oldData.getPrice() * oldData.getVolume());
                    currencyPairToTotalVolume.get(currencyPair)
                        .addAndGet(-oldData.getVolume());
                }

                if (pricesRemovedFromStream) {
                    long totalVolume = currencyPairToTotalVolume.get(currencyPair).get();
                    if (totalVolume > 0) {
                        calculateVWAP(currencyPair);
                    } else {
                        currencyPairToVWAP.remove(currencyPair);
                        currencyPairToPriceStream.remove(currencyPair);
                        currencyPairToTotalWeightedPrice.remove(currencyPair);
                        currencyPairToTotalVolume.remove(currencyPair);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during price cleanup: {}", e.getMessage());
        }
    }

    private boolean currencyStreamContainsOutdatedPrices(Deque<CurrencyPriceData> currencyPairStream, Instant cutoffTime) {
        return !currencyPairStream.isEmpty() && currencyPairStream.peekLast().getTimestamp().isBefore(cutoffTime);
    }

    public Map<String, Double> getCurrencyPairToVWAP() {
        return currencyPairToVWAP;
    }

    public void shutdown() {
        try {
            scheduledExecutorService.shutdown();
            if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutorService.shutdownNow();
            LOGGER.error("Error during shutdown: {}", e.getMessage());
        }
    }
}
