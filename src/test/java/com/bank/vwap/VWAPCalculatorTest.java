package com.bank.vwap;

import com.bank.util.DateTimeUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.ZoneId;

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
}