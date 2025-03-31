package com.bank.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateTimeUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(DateTimeUtil.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    public static Instant convertToInstant(String timeString, ZoneId zoneId) throws DateTimeParseException {
        try {
            LocalTime localTime = LocalTime.parse(timeString.toLowerCase(), FORMATTER);
            LocalDate today = LocalDate.now(zoneId);
            return LocalDateTime.of(today, localTime).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            LOGGER.error("Invalid time format: ", timeString);
            // send email alert to propagate error to team with details of price entry
            return null;
        }
    }
}