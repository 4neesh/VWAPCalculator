## Overview
The VWAP (Volume Weighted Average Price) Calculator is a Java application that generates currency price updates and calculates the VWAP for each currency pair.

## Prerequisites
Before you begin, ensure you have the following installed on your machine:

- **Java Development Kit (JDK)**: Version 11 or higher. 
- **Maven**: to manage dependencies and build the project easily.

## Setting Up the Environment

1. **Clone the Repository**
   Clone the repository to your local machine using Git:
   ```bash
   git clone https://github.com/4neesh/VWAPCalculator.git
   ```

2. **Install Dependencies**
   Change into the project directory and install the project with maven:
   ```bash
   cd VWAPCalculator
   mvn clean install
   ```
   The above command will download the necessary dependencies specified in the `pom.xml` file.

## Running the Application

1. **Navigate to the Application Directory**

2. **Run the Application**
Execute the main class using the following command:
[WITHOUT LOGGING]
   ```bash
   java -jar target/VWAPCalculator-1.0-SNAPSHOT.jar
   ```
[WITH LOGGING]

   ```bash
   java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar target/VWAPCalculator-1.0-SNAPSHOT.jar 
   ```

3. **Observe Output**
   When run with debug logging, the application will process a series of price updates and print the updated VWAP values for each currency pair to the console along with its most recent price update.

## Application Design
- see DesignNotes.md 