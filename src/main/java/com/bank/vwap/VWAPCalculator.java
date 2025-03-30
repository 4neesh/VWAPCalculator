package com.bank.vwap;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bank.vwap.VWAPConfig.CUTOFF_SECONDS;

public class VWAPCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VWAPCalculator.class);

    private final BlockingQueue<CurrencyPriceData> priceUpdateQueue = new LinkedBlockingQueue<>();

    private final Map<String, Double> currencyPairToVWAP = new ConcurrentHashMap<>();
    private final Map<String, Deque<CurrencyPriceData>> currencyPairToPriceStream = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> currencyPairToTotalWeightedPrice = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> currencyPairToTotalVolume = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public VWAPCalculator(){
        cleanupScheduledExecutor.scheduleAtFixedRate(this::clearCutoffPricesForAllCurrencyPairs, 0, CUTOFF_SECONDS, TimeUnit.SECONDS);
        startProcessingThread();
    }

    public void sendVWAPForCurrencyPair(CurrencyPriceData currencyPriceData) {
        priceUpdateQueue.offer(currencyPriceData);
    }

    private void startProcessingThread() {
        executorService.submit(() -> {
            while (true) {
                try {
                    CurrencyPriceData update = priceUpdateQueue.take(); // Blocks until an update is available
                    processVWAPForCurrencyPair(update);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // Exit the loop if interrupted
                }
            }
        });
    }

    public void processVWAPForCurrencyPair(CurrencyPriceData currencyPriceData) {
        try {
            Deque<CurrencyPriceData> currencyPairStream = currencyPairToPriceStream.computeIfAbsent(
                    currencyPriceData.getCurrencyPair(), k -> new ConcurrentLinkedDeque<>()
            );
            currencyPairStream.addFirst(currencyPriceData);

            currencyPairToTotalWeightedPrice.computeIfAbsent(currencyPriceData.getCurrencyPair(), k -> new DoubleAdder())
                .add(currencyPriceData.getPrice() * currencyPriceData.getVolume());
            currencyPairToTotalVolume.computeIfAbsent(currencyPriceData.getCurrencyPair(), k -> new AtomicLong(0))
                .addAndGet(currencyPriceData.getVolume());

            calculateVWAP(currencyPriceData.getCurrencyPair(), currencyPriceData.getTimestamp());

        } catch (Exception e) {
            LOGGER.error("Error processing price update for {}: {}", currencyPriceData.getCurrencyPair(), e.getMessage());
            // send email alert to propagate error to team with details of price entry
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
            // send email alert to propagate error to team with details of price entry
        }
    }

    protected void removePricesBeforeCutoff(String currencyPair, Instant timestamp) {
        try {
            Instant cutoffTime = timestamp.minusSeconds(CUTOFF_SECONDS);
            Deque<CurrencyPriceData> priceStream = currencyPairToPriceStream.get(currencyPair);
            boolean pricesRemovedFromStream = false;
            Iterator<CurrencyPriceData> iterator = priceStream.descendingIterator();

            //Step 1 - remove old items from deque
            while (iterator.hasNext()) {
                CurrencyPriceData data = iterator.next();
                if (data.getTimestamp().isBefore(cutoffTime)) {
                    pricesRemovedFromStream = true;
                    priceStream.remove(data);
                    currencyPairToTotalWeightedPrice.get(currencyPair)
                            .add(-data.getPrice() * data.getVolume());
                    currencyPairToTotalVolume.get(currencyPair)
                            .addAndGet(-data.getVolume());
                } else {
                    break;
                }
            }

            // Step 3 - Cleanup currency pairs without prices within cutoff time
            if (pricesRemovedFromStream && currencyPairToTotalVolume.get(currencyPair).get() <= 0) {
                    currencyPairToVWAP.remove(currencyPair);
                    currencyPairToPriceStream.remove(currencyPair);
                    currencyPairToTotalWeightedPrice.remove(currencyPair);
                    currencyPairToTotalVolume.remove(currencyPair);
            }

        } catch (Exception e) {
            LOGGER.error("Error during price cleanup: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    protected void clearCutoffPricesForAllCurrencyPairs(){
        Instant currentTime = Instant.now();
        for(String currencyPair: currencyPairToPriceStream.keySet()){
            this.removePricesBeforeCutoff(currencyPair, currentTime);
        }
    }

    protected Map<String, Double> getCurrencyPairToVWAP() {
        return currencyPairToVWAP;
    }

    public Map<String, Deque<CurrencyPriceData>> getCurrencyPairToPriceStream() {
        return currencyPairToPriceStream;
    }

    public Map<String, DoubleAdder> getCurrencyPairToTotalWeightedPrice() {
        return currencyPairToTotalWeightedPrice;
    }

    public Map<String, AtomicLong> getCurrencyPairToTotalVolume() {
        return currencyPairToTotalVolume;
    }

    public void shutdown(){
        this.cleanupScheduledExecutor.shutdown();
    }
}

