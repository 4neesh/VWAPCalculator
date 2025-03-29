package com.bank.vwap;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

public class VWAPCalculator {

    private Map<String, Double> currencyPairToVWAP = new ConcurrentHashMap<>();

    private Map<String, Deque<CurrencyPriceData>> currencyPairToPriceStream = new ConcurrentHashMap<>();
    private Map<String, Double> currencyPairToTotalWeightedPrice = new ConcurrentHashMap<>();
    private Map<String, Long> currencyPairToTotalVolume = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    public VWAPCalculator(){
        scheduledExecutorService.scheduleAtFixedRate(this::removePricesBeforeCutoff, 1, 1, TimeUnit.SECONDS);
    }

    public void processPriceUpdate(String currencyPair, double price, long volume){
        CurrencyPriceData currencyPriceData = new CurrencyPriceData(Instant.now(), currencyPair, price, volume);
        Deque<CurrencyPriceData> currencyPairStream = currencyPairToPriceStream.computeIfAbsent(currencyPair, currencyPairKey -> new ConcurrentLinkedDeque<>());
        currencyPairStream.addFirst(currencyPriceData);

        currencyPairToTotalWeightedPrice.compute(currencyPair, (currencyPairKey, totalWeightedPrice) -> (totalWeightedPrice == null ? 0 : totalWeightedPrice) + price * volume);
        currencyPairToTotalVolume.compute(currencyPair, (currencyPairKey, totalVolume) -> (null == totalVolume ? 0 : totalVolume) + volume);

        double vwap = currencyPairToTotalWeightedPrice.get(currencyPair) / currencyPairToTotalVolume.get(currencyPair);
        currencyPairToVWAP.put(currencyPair, vwap);
        System.out.printf("Updated %s to VWAP of %s\n", currencyPair, vwap);
    }

    private void removePricesBeforeCutoff() {
        //1 hour in seconds = 60*60 = 3600
        Instant cutoffTime = Instant.now().minusSeconds(3600);

        for (Map.Entry<String, Deque<CurrencyPriceData>> entry : currencyPairToPriceStream.entrySet()) {
            String currencyPair = entry.getKey();
            Deque<CurrencyPriceData> currencyPairStream = entry.getValue();
            boolean pricesRemovedFromStream = false;

            while (currencyStreamContainsOutdatedPrices(currencyPairStream, cutoffTime)) {
                CurrencyPriceData oldData = currencyPairStream.pollLast();
                pricesRemovedFromStream = true;
                currencyPairToTotalWeightedPrice.compute(currencyPair, (currencyPairKey, totalWeightedPrice) -> totalWeightedPrice - oldData.getPrice() * oldData.getVolume());
                currencyPairToTotalVolume.compute(currencyPair, (currencyPairKey, totalVolume) -> totalVolume - oldData.getVolume());
            }
            if (pricesRemovedFromStream && currencyPairToTotalVolume.getOrDefault(currencyPair, 0L) > 0) {
                double vwap = currencyPairToTotalWeightedPrice.get(currencyPair) / currencyPairToTotalVolume.get(currencyPair);
                currencyPairToVWAP.put(currencyPair, vwap);
            } else if (currencyPairToTotalVolume.getOrDefault(currencyPair, 0L) == 0) {
                currencyPairToVWAP.remove(currencyPair);
            }
        }
    }

    private boolean currencyStreamContainsOutdatedPrices(Deque<CurrencyPriceData> currencyPairStream, Instant cutoffTime) {
        return !currencyPairStream.isEmpty() && currencyPairStream.peekLast().getTimestamp().isBefore(cutoffTime);
    }

    public Map<String, Double> getCurrencyPairToVWAP() {
        return currencyPairToVWAP;
    }

    public void shutdown() {
        this.scheduledExecutorService.shutdown();
    }
}
