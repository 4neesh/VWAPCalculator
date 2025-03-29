package com.bank.vwap;

import java.time.Instant;
import java.util.*;
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

    private final ScheduledExecutorService scheduledPriceRemovalExecutor = Executors.newSingleThreadScheduledExecutor();

    public VWAPCalculator() {
        scheduledPriceRemovalExecutor.scheduleAtFixedRate(this::removePricesBeforeCutoff, 0, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
            
            currencyPairToPriceStream.entrySet().parallelStream().forEach(entry -> {
                String currencyPair = entry.getKey();
                Deque<CurrencyPriceData> currencyPairStream = entry.getValue();
                boolean pricesRemovedFromStream = false;
                
                Iterator<CurrencyPriceData> iterator = currencyPairStream.iterator();
                List<CurrencyPriceData> toRemove = new ArrayList<>();

                //Step 1 - remove old items from deque
                while (iterator.hasNext()) {
                    CurrencyPriceData data = iterator.next();
                    if (data.getTimestamp().isBefore(cutoffTime)) {
                        toRemove.add(data);
                    } else {
                        break;
                    }
                }

                //Step 2 - update totalVolume and totalWeightedPrice
                if (!toRemove.isEmpty()) {
                    for (CurrencyPriceData oldData : toRemove) {
                        if (currencyPairStream.remove(oldData)) {
                            pricesRemovedFromStream = true;
                            // Use atomic operations with minimal locking
                            currencyPairToTotalWeightedPrice.get(currencyPair)
                                .add(-oldData.getPrice() * oldData.getVolume());
                            currencyPairToTotalVolume.get(currencyPair)
                                .addAndGet(-oldData.getVolume());
                        }
                    }

                    
                    // Step 3 - Recalculate VWAP calculation or cleanup
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
            });
        } catch (Exception e) {
            LOGGER.error("Error during price cleanup: {}", e.getMessage());
        }
    }

    public Map<String, Double> getCurrencyPairToVWAP() {
        return currencyPairToVWAP;
    }

    public void shutdown() {
        try {
            scheduledPriceRemovalExecutor.shutdown();
            if (!scheduledPriceRemovalExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledPriceRemovalExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledPriceRemovalExecutor.shutdownNow();
            LOGGER.error("Error during shutdown: {}", e.getMessage());
        }
    }
}

class CurrencyPriceData {
    private Instant timestamp;
    private String currencyPair;
    private double price;
    private long volume;

    public double getPrice() {
        return price;
    }

    public long getVolume() {
        return volume;
    }

    public CurrencyPriceData(Instant timestamp, String currencyPair, double price, long volume) {
        this.timestamp = timestamp;
        this.currencyPair = currencyPair;
        this.price = price;
        this.volume = volume;
    }
    public Instant getTimestamp() {
        return timestamp;
    }
}

