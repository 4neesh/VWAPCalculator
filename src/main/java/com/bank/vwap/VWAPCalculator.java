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
            CurrencyData data = currencyPairData.computeIfAbsent(
                    currencyPriceData.getCurrencyPair(), k -> new CurrencyData()
            );
            
            data.getPriceStream().addFirst(currencyPriceData);
            data.getTotalWeightedPrice().add(currencyPriceData.getPrice() * currencyPriceData.getVolume());
            data.getTotalVolume().addAndGet(currencyPriceData.getVolume());

            calculateVWAP(currencyPriceData.getCurrencyPair(), currencyPriceData.getTimestamp());
            logProcessedResult(currencyPriceData);

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

    public void shutdownExecutors(){
        this.priceFeedConsumerExecutorService.shutdown();
        this.cleanupScheduledExecutor.shutdown();
    }

    public Map<String, CurrencyData> getCurrencyPairData() {
        return currencyPairData;
    }

    private void logProcessedResult(CurrencyPriceData currencyPriceData) {
        if (LOGGER.isDebugEnabled()) {
            Double vwap = currencyPairData.get(currencyPriceData.getCurrencyPair()).getVwap();
            LOGGER.debug("Price Data: [{}, {}, {}, {}] created VWAP of {}",
                    currencyPriceData.getCurrencyPair(),
                    currencyPriceData.getPrice(),
                    currencyPriceData.getVolume(),
                    currencyPriceData.getTimestamp(),
                    vwap);
        }
    }
}

