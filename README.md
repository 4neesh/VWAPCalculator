

### Programming Exercise – VWAP calculation Description:

Given a stream of price data for multiple currency pairs in the form of:

[Timestamp, Currency-pair, Price, Volume]


We would like the solution to output the VWAP calculated over the input stream. 

- The VWAP should be calculated on 1 hours’ worth of price data.
- The VWAP should be calculated for each unique currency pair.
- The VWAP should be calculated each time we receive a new price update.

Example data:

An example stream of price data would be something like the following:

| TIMESTAMP | CURRENCY-PAIR | PRICE   | VOLUME     |
|-----------|---------------|---------|------------|
| 9:30 AM   | AUD/USD       | 0.6905  | 106,198    |
| 9:31 AM   | USD/JPY       | 142.497 | 30,995     |
| 9:32 AM   | USD/JPY       | 139.392 | 2,890,000  |
| 9:33 AM   | AUD/USD       | 0.6899  | 444,134    |
| 9:34 AM   | NZD/GBP       | 0.4731  | 64, 380    |
| 9:35 AM   | NZD/GBP       | 0.4725  | 8, 226,295 |

Considerations:
- The incoming stream of data will be significant, and care should be taken to avoid JVM crash.
- There can be many thousands of price updates per second.
- The VWAP needs to be calculated in real time as it may be used by decision making
algorithms.

Deliverable:
- Solve the problem as though it were “production level” code.
- The solution submitted should include source-code, configuration and any tests you
deem necessary.
- Solve the problem in java.
- It is not required to provide any graphical interface.