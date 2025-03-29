## Overview
The VWAP (Volume Weighted Average Price) Calculator is a Java application that processes currency price updates and calculates the VWAP for various currency pairs.

## Prerequisites
Before you begin, ensure you have the following installed on your machine:

- **Java Development Kit (JDK)**: Version 11 or higher. 
- **Maven**: to manage dependencies and build the project easily.

## Setting Up the Environment

1. **Clone the Repository**
   Clone the repository to your local machine using Git:
   ```bash
   git clone <repository-url>
   cd VWAPCalculator
   ```

2. **Install Dependencies**
   Using Maven, navigate to the project directory and run:
   ```bash
   mvn install
   ```
   This will download the necessary dependencies specified in the `pom.xml` file.

## Running the Application

1. **Navigate to the Application Directory**
   Ensure you are in the directory where the compiled classes are located. 
   If you used Maven, this will typically be in the `target/classes` directory.

2. **Run the Application**
   Execute the main class using the following command:
   ```bash
   java com.bank.vwap.Application
   ```

3. **Observe Output**
   The application will process a series of price updates and print the updated VWAP values for each currency pair to the console.

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