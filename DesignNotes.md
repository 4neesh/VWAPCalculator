## Application Functionality
- Performs VWAP calculation on live price stream for each currency pair
- VWAP uses prices reported within the last hour
- VWAP is recalculated using the past hour of prices each time a new price is reported for the currency pair

## Application Run
- Running the Application class will generate 1000 price/volume combinations across 3 currency pairs (AUD/USD, USD/JPY & NZD/GBP) for 10 seconds into a BlockingQueue
- The VWAPCalculator class will listen and process the currency pair prices to generate a VWAP. The updated VWAP is reported to the console if run with the debug logging mode

## Developer Notes

### Achieving Low Latency and High Throughput
- LinkedBlockingQueues for storing priceUpdates
- ConcurrentHashMap for storing lookup values for currencyPairs
- ConcurrentLinkedDeque for storing priceStream for each currency
- DoubleAdder for low-contention, high-throughput weighted price calculations
- AtomicLong for thread-safe, non-blocking volume calculations
- Multi-threading: Separate threads used for producing price stream, consuming and processing price stream and removing prices and currencies after cutoff periods

### Logging
- SLF4J using debug mode only for prices to avoid flooding production logs with data
- ERROR messages are used when encountering exceptions

### Date Handling
- Supports receiving dates in the format 'h:mm a', which is converted with the DateTimeUtill class using the current date.
- Application class uses Instant.now() to simulate transactions with millisecond granularity. 

### Configuration
- Properties file read in through maven. Used to configure cutoff time

### Error Handling
- Exceptions are not thrown, they are logged
- Logging prevents further complications of valid prices not being processed in real-time
- ERROR messages are used with the logger for processing/observability

### Assumptions
- Timestamp does not provide a date, and will be processed as the current day
- Timezone is Australia/Sydney
- While timestamp is given without seconds, it has potential to be given with seconds and milliseconds
- Time is only provided in `h:mm a` format, more likely to use milliseconds in real time.

### Enhancements to consider
- Disruptor (LMAX) for LinkedBlockingQueue
- Controlled end-to-end testing
- Analysis with Java Flight Recorder/JProfiler

### Testing
- Performed on main logic class: VWAPCalculator
- Uses JUnit and Mockito