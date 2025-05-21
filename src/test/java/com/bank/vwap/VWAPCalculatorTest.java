package com.bank.vwap;

import com.bank.util.DateTimeUtil;
import com.bank.util.PriceStatistics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bank.vwap.VWAPCalculator.PRICE_TIMEZONE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class VWAPCalculatorTest {
    private VWAPCalculator calculator;

    @Test
    public void testCalculateVWAPForIdenticalCurrencyPairs() {
        calculator = new VWAPCalculator(3600);

        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:30 AM","AUD/USD", 0.75, 1000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM","AUD/USD", 0.76, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:32 AM","AUD/USD", 0.77, 3000));

        //VWAP = sum of (volume * price) / sum of volume
        double expectedVWAP = (0.75 * 1000 + 0.76 * 2000 + 0.77 * 3000) / (1000 + 2000 + 3000);
        assertEquals(expectedVWAP, calculator.getCurrencyPairData().get("AUD/USD").getVwap(), 0.0001);
    }

    @Test
    public void testCalculateVWAPForMultipleCurrencyPairs() {
        calculator = new VWAPCalculator(3600);

        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:30 AM","AUD/USD", 0.75, 1000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM","USD/JPY", 110.0, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:32 AM","AUD/USD", 0.76, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:33 AM","USD/JPY", 111.0, 3000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:34 AM","NZD/GBP", 0.55, 1500));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:35 AM","NZD/GBP", 0.56, 2500));

        double expectedAUDVWAP = (0.75 * 1000 + 0.76 * 2000) / (1000 + 2000);
        double expectedUSDJPYVWAP = (110.0 * 2000 + 111.0 * 3000) / (2000 + 3000);
        double expectedNZDGBPVWAP = (0.55 * 1500 + 0.56 * 2500) / (1500 + 2500);

        assertEquals(expectedAUDVWAP, calculator.getCurrencyPairData().get("AUD/USD").getVwap(), 0.0001);
        assertEquals(expectedUSDJPYVWAP, calculator.getCurrencyPairData().get("USD/JPY").getVwap(), 0.0001);
        assertEquals(expectedNZDGBPVWAP, calculator.getCurrencyPairData().get("NZD/GBP").getVwap(), 0.0001);
    }

    @Test
    public void testVWAPRecalculationAfterCutoffExpiration() throws InterruptedException {
        //reinitialise as spy to verify method calls and use new CUTOFF
        calculator = new VWAPCalculator(1);
        calculator = spy(calculator);

        //ensure scheduled cleanup does not remove cutoff prices
        calculator.shutdownExecutors();

        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:30 AM", "AUD/USD", 0.75, 1000));

        //Sleep for 2 seconds to ensure old data is removed
        Thread.sleep(2000);
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM", "AUD/USD", 0.76, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM", "AUD/USD", 0.77, 3000));

        double expectedVWAPAfterCutoff = (0.76 * 2000 + 0.77 * 3000) / (2000 + 3000);
        assertEquals(expectedVWAPAfterCutoff, calculator.getCurrencyPairData().get("AUD/USD").getVwap(), 0.0001);

        //verify cutoff not performed by scheduled executor
        Mockito.verify(calculator, never()).clearCutoffPricesForAllCurrencyPairs();
    }

    @Test
    public void testRemovePricesAfterCutoffExpiration() {
        calculator = new VWAPCalculator(3600);

        String currencyPair = "EUR/USD";
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("1:00 AM",currencyPair, 1.7, 2000));

        //current time is more than 1 hour after the prior price was added
        Instant currentTime = DateTimeUtil.convertToInstant("9:00 am", ZoneId.of(PRICE_TIMEZONE));
        calculator.removePricesBeforeCutoff(currencyPair, currentTime);

        assertFalse(calculator.getCurrencyPairData().containsKey(currencyPair));
    }

    @Test
    public void testCleanupRemovesExpiredCurrencyPairs() throws InterruptedException {
        //initialise calculator with 1 second cutoff
        calculator = new VWAPCalculator(1);

        calculator.processVWAPForCurrencyPair(new CurrencyPriceData(Instant.now(), "AUD/USD", 1.7, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData(Instant.now(), "NZD/GBP", 0.7, 1400));
        assertTrue(calculator.getCurrencyPairData().containsKey("AUD/USD"));
        assertTrue(calculator.getCurrencyPairData().containsKey("NZD/GBP"));

        //sleep thread for 2 seconds to allow scheduled cleanup to take place
        Thread.sleep(2000);

        assertTrue(calculator.getCurrencyPairData().isEmpty());
    }

    @Test
    public void testRemovePricesBeforeCutoff_MixedDataset() throws NoSuchFieldException, IllegalAccessException {
        // Initialize calculator with a cutoff of 3600 seconds (1 hour) to simulate a realistic window
        VWAPCalculator calculator = new VWAPCalculator(3600);

        String currencyPair = "AUD/USD";

        // Define a reference timestamp for removal (using Instant.now() for realism, but offsets ensure determinism)
        Instant referenceTimestamp = Instant.now();

        // Calculate the cutoff time explicitly (referenceTimestamp - 3600 seconds)
        Instant cutoffTime = referenceTimestamp.minusSeconds(3600);

        // Create 3 outdated prices (timestamps before cutoffTime)
        CurrencyPriceData outdated1 = new CurrencyPriceData(cutoffTime.minusSeconds(100), currencyPair, 1.0, 100);
        CurrencyPriceData outdated2 = new CurrencyPriceData(cutoffTime.minusSeconds(200), currencyPair, 1.1, 200);
        CurrencyPriceData outdated3 = new CurrencyPriceData(cutoffTime.minusSeconds(300), currencyPair, 1.2, 300);

        // Create 3 recent prices (timestamps after cutoffTime)
        CurrencyPriceData recent1 = new CurrencyPriceData(cutoffTime.plusSeconds(100), currencyPair, 1.0, 400);
        CurrencyPriceData recent2 = new CurrencyPriceData(cutoffTime.plusSeconds(200), currencyPair, 1.4, 500);
        CurrencyPriceData recent3 = new CurrencyPriceData(cutoffTime.plusSeconds(300), currencyPair, 1.5, 600);

        // Add all prices using processVWAPForCurrencyPair to simulate real usage
        calculator.processVWAPForCurrencyPair(outdated1);
        calculator.processVWAPForCurrencyPair(outdated2);
        calculator.processVWAPForCurrencyPair(outdated3);
        calculator.processVWAPForCurrencyPair(recent1);
        calculator.processVWAPForCurrencyPair(recent2);
        calculator.processVWAPForCurrencyPair(recent3);

        // Get CurrencyData for assertions
        CurrencyData data = calculator.getCurrencyPairData().get(currencyPair);

        // Use reflection to access the private currencyPairStats map for testing
        // Note: In production, consider adding a package-private getter to VWAPCalculator, e.g., PriceStatistics getPriceStatistics(String currencyPair)
        Field statsField = VWAPCalculator.class.getDeclaredField("currencyPairStats");
        statsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, PriceStatistics> currencyPairStats = (Map<String, PriceStatistics>) statsField.get(calculator);

        // Capture initial PriceStatistics state before removal (should reflect all 6 prices)
        PriceStatistics initialStats = currencyPairStats.get(currencyPair);
        double initialHigh = initialStats.getHighPrice();  // Expected: 1.5 (from recent3)
        double initialLow = initialStats.getLowPrice();    // Expected: 1.0 (from outdated1)
        double initialAverage = initialStats.getAveragePrice();  // Expected: average of all 6 prices

        // Invoke the method under test with the reference timestamp
        calculator.removePricesBeforeCutoff(currencyPair, referenceTimestamp);

        // Assert 1: Only recent prices remain in the price stream (size and specific timestamps)
        Deque<CurrencyPriceData> priceStream = data.getPriceStream();
        assertEquals(3, priceStream.size());  // Only 3 recent entries should remain
        List<Instant> remainingTimestamps = priceStream.stream()
                .map(CurrencyPriceData::getTimestamp)
                .collect(Collectors.toList());
        assertTrue("Recent timestamp 1 should remain", remainingTimestamps.contains(recent1.getTimestamp()));
        assertTrue("Recent timestamp 2 should remain", remainingTimestamps.contains(recent2.getTimestamp()));
        assertTrue("Recent timestamp 3 should remain", remainingTimestamps.contains(recent3.getTimestamp()));
        assertFalse("Outdated timestamp 1 should be removed", remainingTimestamps.contains(outdated1.getTimestamp()));
        assertFalse("Outdated timestamp 2 should be removed", remainingTimestamps.contains(outdated2.getTimestamp()));
        assertFalse("Outdated timestamp 3 should be removed", remainingTimestamps.contains(outdated3.getTimestamp()));

        // Assert 2: totalVolume and totalWeightedPrice reflect only the recent prices
        long expectedVolume = 400 + 500 + 600;  // Sum of recent volumes
        double expectedWeightedPrice = (1.0 * 400) + (1.4 * 500) + (1.5 * 600);  // Sum of recent (price * volume)
        assertEquals("Total volume should match recent prices only", expectedVolume, data.getTotalVolume().get());
        assertEquals("Total weighted price should match recent prices only", expectedWeightedPrice, data.getTotalWeightedPrice().sum(), 0.0001);

        // Assert 3: PriceStatistics remains unchanged and reflects all original prices (not recalculated)
        PriceStatistics afterStats = currencyPairStats.get(currencyPair);
        assertEquals("High price should remain unchanged", initialHigh, afterStats.getHighPrice(), 0.0001);
        assertEquals("Low price should remain unchanged", initialLow, afterStats.getLowPrice(), 0.0001);
        assertEquals("Average price should remain unchanged", initialAverage, afterStats.getAveragePrice(), 0.0001);

        // Reset reflection access (good practice)
        statsField.setAccessible(false);
    }
}