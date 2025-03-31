package com.bank.vwap;

import com.bank.util.DateTimeUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;

import static com.bank.vwap.VWAPConfig.TIMESTAMP_TIMEZONE;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class VWAPCalculatorTest {
    private VWAPCalculator calculator;

    @Before
    public void setUp() {
        calculator = new VWAPCalculator();
    }

    @Test
    public void testProcessPriceUpdate() {
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:30 AM","AUD/USD", 0.75, 1000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM","AUD/USD", 0.76, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:32 AM","AUD/USD", 0.77, 3000));

        //VWAP = sum of (volume * price) / sum of volume
        double expectedVWAP = (0.75 * 1000 + 0.76 * 2000 + 0.77 * 3000) / (1000 + 2000 + 3000);
        assertEquals(expectedVWAP, calculator.getCurrencyPairToVWAP().get("AUD/USD"), 0.0001);
    }

    @Test
    public void testMultipleCurrencyPairs() {
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:30 AM","AUD/USD", 0.75, 1000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM","USD/JPY", 110.0, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:32 AM","AUD/USD", 0.76, 2000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:33 AM","USD/JPY", 111.0, 3000));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:34 AM","NZD/GBP", 0.55, 1500));
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:35 AM","NZD/GBP", 0.56, 2500));

        double expectedAUDVWAP = (0.75 * 1000 + 0.76 * 2000) / (1000 + 2000);
        double expectedUSDJPYVWAP = (110.0 * 2000 + 111.0 * 3000) / (2000 + 3000);
        double expectedNZDGBPVWAP = (0.55 * 1500 + 0.56 * 2500) / (1500 + 2500);

        assertEquals(expectedAUDVWAP, calculator.getCurrencyPairToVWAP().get("AUD/USD"), 0.0001);
        assertEquals(expectedUSDJPYVWAP, calculator.getCurrencyPairToVWAP().get("USD/JPY"), 0.0001);
        assertEquals(expectedNZDGBPVWAP, calculator.getCurrencyPairToVWAP().get("NZD/GBP"), 0.0001);
    }

    @Test
    public void testRecalculateVWAPWithCutoffPricesInStream() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        Field field = VWAPConfig.class.getDeclaredField("CUTOFF_SECONDS");

        try {
            //set CUTOFF_TIME to 1 second
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            field.set(null, 1);

            //reinitialise as spy to verify method calls and use new CUTOFF
            calculator = spy(VWAPCalculator.class);

            //ensure scheduled cleanup does not remove cutoff prices
            calculator.shutdownExecutors();

            calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:30 AM", "AUD/USD", 0.75, 1000));

            //Sleep for 1.2 seconds to ensure old data is removed
            Thread.sleep(1200);
            calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM", "AUD/USD", 0.76, 2000));
            calculator.processVWAPForCurrencyPair(new CurrencyPriceData("9:31 AM", "AUD/USD", 0.77, 3000));

            double expectedVWAPAfterCutoff = (0.76 * 2000 + 0.77 * 3000) / (2000 + 3000);
            assertEquals(expectedVWAPAfterCutoff, calculator.getCurrencyPairToVWAP().get("AUD/USD"), 0.0001);

            //verify cutoff not performed by scheduled executor
            Mockito.verify(calculator, never()).clearCutoffPricesForAllCurrencyPairs();
        }
        finally{
            field.set(null, 3600);
        }
    }

    @Test
    public void testRemovePricesOfExpiredCutoff() {

        String currencyPair = "EUR/USD";
        calculator.processVWAPForCurrencyPair(new CurrencyPriceData("1:00 AM","AUD/USD", 1.7, 2000));

        //current time is more than 1 hour after the prior price was added
        Instant currentTime = DateTimeUtil.convertToInstant("9:00 am", ZoneId.of(TIMESTAMP_TIMEZONE));
        calculator.removePricesBeforeCutoff(currencyPair, currentTime);

        assertFalse(calculator.getCurrencyPairToVWAP().containsKey(currencyPair));
        assertFalse(calculator.getCurrencyPairToPriceStream().containsKey(currencyPair));
        assertFalse(calculator.getCurrencyPairToTotalVolume().containsKey(currencyPair));
        assertFalse(calculator.getCurrencyPairToTotalWeightedPrice().containsKey(currencyPair));
    }

    @Test
    public void scheduledCleanupRemovesUnusedCurrencyPairs() throws NoSuchFieldException, IllegalAccessException, InterruptedException {

        Field field = VWAPConfig.class.getDeclaredField("CUTOFF_SECONDS");
        try {
            //set CUTOFF_TIME to 1 second
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            field.set(null, 1);

            calculator = spy(VWAPCalculator.class);

            calculator.processVWAPForCurrencyPair(new CurrencyPriceData(Instant.now(), "AUD/USD", 1.7, 2000));
            calculator.processVWAPForCurrencyPair(new CurrencyPriceData(Instant.now(), "NZD/GBP", 0.7, 1400));
            assertTrue(calculator.getCurrencyPairToVWAP().containsKey("AUD/USD"));
            assertTrue(calculator.getCurrencyPairToVWAP().containsKey("NZD/GBP"));
            //sleep thread for 2 seconds to allow scheduled cleanup to take place
            Thread.sleep(2000);

            assertTrue(calculator.getCurrencyPairToVWAP().isEmpty());
            //verify the clear cutoff prices for all currencies is called
            verify(calculator, atLeastOnce()).clearCutoffPricesForAllCurrencyPairs();
        }
        finally{
            field.set(null, 3600);
        }

    }
}