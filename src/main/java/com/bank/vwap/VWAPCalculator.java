package com.bank.vwap;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import com.bank.util.DateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bank.vwap.VWAPConfig.CUTOFF_SECONDS;
import static com.bank.vwap.VWAPConfig.TIMESTAMP_TIMEZONE;

public class VWAPCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VWAPCalculator.class);
    private static ZoneId zoneId = ZoneId.of(TIMESTAMP_TIMEZONE);

    private final Map<String, Double> currencyPairToVWAP = new ConcurrentHashMap<>();
    private final Map<String, Deque<CurrencyPriceData>> currencyPairToPriceStream = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> currencyPairToTotalWeightedPrice = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> currencyPairToTotalVolume = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduledExecutor = Executors.newSingleThreadScheduledExecutor();

    public VWAPCalculator(){
        cleanupScheduledExecutor.scheduleAtFixedRate(this::clearCutoffPricesForAllCurrencyPairs, 0, CUTOFF_SECONDS, TimeUnit.SECONDS);
    }

    public void processVWAPForCurrencyPair(String timestamp, String currencyPair, double price, long volume) {
        try {
            Instant timestampInstant = DateTimeUtil.convertToInstant(timestamp.toLowerCase(), zoneId);
            CurrencyPriceData currencyPriceData = new CurrencyPriceData(timestampInstant, currencyPair, price, volume);

            Deque<CurrencyPriceData> currencyPairStream = currencyPairToPriceStream.computeIfAbsent(
                currencyPair, k -> new ConcurrentLinkedDeque<>()
            );
            currencyPairStream.addFirst(currencyPriceData);

            currencyPairToTotalWeightedPrice.computeIfAbsent(currencyPair, k -> new DoubleAdder())
                .add(price * volume);
            currencyPairToTotalVolume.computeIfAbsent(currencyPair, k -> new AtomicLong(0))
                .addAndGet(volume);

            calculateVWAP(currencyPair, timestampInstant);

        } catch (Exception e) {
            LOGGER.error("Error processing price update for {}: {}", currencyPair, e.getMessage());
            throw new RuntimeException("Failed to process price update", e);
        }
    }

    private void calculateVWAP(String currencyPair, Instant timestamp) {
        try {
            removePricesBeforeCutoff(currencyPair, timestamp);
            double totalWeightedPrice = currencyPairToTotalWeightedPrice.get(currencyPair).sum();
            long totalVolume = currencyPairToTotalVolume.get(currencyPair).get();
            
            if (totalVolume > 0) {
                double vwap = totalWeightedPrice / totalVolume;
                currencyPairToVWAP.put(currencyPair, vwap);
                LOGGER.info("[" + timestamp + "] Updated " + currencyPair + " to VWAP of " + vwap);
            }
        } catch (Exception e) {
            LOGGER.error("Error calculating VWAP for {}: {}", currencyPair, e.getMessage());
            throw e;
        }
    }

    private void removePricesBeforeCutoff(String currencyPair, Instant timestamp) {
        try {
            Instant cutoffTime = timestamp.minusSeconds(CUTOFF_SECONDS);
            Deque<CurrencyPriceData> priceStream = currencyPairToPriceStream.get(currencyPair);
            boolean pricesRemovedFromStream = false;
            Iterator<CurrencyPriceData> iterator = priceStream.iterator();
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
                    if (priceStream.remove(oldData)) {
                        pricesRemovedFromStream = true;
                        // Use atomic operations with minimal locking
                        currencyPairToTotalWeightedPrice.get(currencyPair)
                            .add(-oldData.getPrice() * oldData.getVolume());
                        currencyPairToTotalVolume.get(currencyPair)
                            .addAndGet(-oldData.getVolume());
                    }
                }

                // Step 3 - Cleanup
                if (pricesRemovedFromStream && currencyPairToTotalVolume.get(currencyPair).get() <= 0) {
                        currencyPairToVWAP.remove(currencyPair);
                        currencyPairToPriceStream.remove(currencyPair);
                        currencyPairToTotalWeightedPrice.remove(currencyPair);
                        currencyPairToTotalVolume.remove(currencyPair);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during price cleanup: {}", e.getMessage());
        }
    }

    private void clearCutoffPricesForAllCurrencyPairs(){
        Instant currentTime = Instant.now();
        for(String currencyPair: currencyPairToPriceStream.keySet()){
            this.removePricesBeforeCutoff(currencyPair, currentTime);
        }
    }

    public Map<String, Double> getCurrencyPairToVWAP() {
        return currencyPairToVWAP;
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

