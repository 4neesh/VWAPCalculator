package com.bank.vwap;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class VWAPCalculatorTest {
    private VWAPCalculator calculator;

    @Before
    public void setUp() {
        calculator = new VWAPCalculator();
    }

    @Test
    public void testProcessPriceUpdate() {
        calculator.processPriceUpdate("9:30 AM","AUD/USD", 0.75, 1000);
        calculator.processPriceUpdate("9:31 AM","AUD/USD", 0.76, 2000);
        calculator.processPriceUpdate("9:32 AM","AUD/USD", 0.77, 3000);

        double expectedVWAP = (0.75 * 1000 + 0.76 * 2000 + 0.77 * 3000) / (1000 + 2000 + 3000);
        assertEquals(expectedVWAP, calculator.getCurrencyPairToVWAP().get("AUD/USD"), 0.01);
    }

    @Test
    public void testMultipleCurrencyPairs() {
        calculator.processPriceUpdate("9:30 AM","AUD/USD", 0.75, 1000);
        calculator.processPriceUpdate("9:31 AM","USD/JPY", 110.0, 2000);
        calculator.processPriceUpdate("9:32 AM","AUD/USD", 0.76, 2000);
        calculator.processPriceUpdate("9:33 AM","USD/JPY", 111.0, 3000);
        calculator.processPriceUpdate("9:34 AM","NZD/GBP", 0.55, 1500);
        calculator.processPriceUpdate("9:35 AM","NZD/GBP", 0.56, 2500);

        double expectedAUDVWAP = (0.75 * 1000 + 0.76 * 2000) / (1000 + 2000);
        double expectedUSDJPYVWAP = (110.0 * 2000 + 111.0 * 3000) / (2000 + 3000);
        double expectedNZDGBPVWAP = (0.55 * 1500 + 0.56 * 2500) / (1500 + 2500);

        assertEquals(expectedAUDVWAP, calculator.getCurrencyPairToVWAP().get("AUD/USD"), 0.01);
        assertEquals(expectedUSDJPYVWAP, calculator.getCurrencyPairToVWAP().get("USD/JPY"), 0.01);
        assertEquals(expectedNZDGBPVWAP, calculator.getCurrencyPairToVWAP().get("NZD/GBP"), 0.01);
    }

    @Test
    @Ignore
    //TODO update test for more realistic run
    public void testRemovePricesBeforeCutoff() throws InterruptedException {
        calculator.processPriceUpdate("9:30 AM","AUD/USD", 0.75, 1000);
        calculator.processPriceUpdate("9:31 AM","AUD/USD", 0.76, 2000);
        Thread.sleep(2000); // Wait for 2 seconds to ensure prices are not removed yet
        calculator.processPriceUpdate("9:32 AM","AUD/USD", 0.77, 3000);

        // Check VWAP before cutoff
        double expectedVWAPBeforeCutoff = (0.75 * 1000 + 0.76 * 2000 + 0.77 * 3000) / (1000 + 2000 + 3000);
        assertEquals(expectedVWAPBeforeCutoff, calculator.getCurrencyPairToVWAP().get("AUD/USD"), 0.01);

        // Wait for prices to be removed (1 hour in the actual implementation, but we can simulate it)
        Thread.sleep(3600 * 1000); // Simulate waiting for 1 hour

        // Check that the VWAP is removed after cutoff
        assertFalse(calculator.getCurrencyPairToVWAP().containsKey("AUD/USD"));
    }

    @Test
    public void testNoPriceUpdates() {
        assertTrue(calculator.getCurrencyPairToVWAP().isEmpty());
    }

}