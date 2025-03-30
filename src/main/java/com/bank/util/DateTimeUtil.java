package com.bank.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateTimeUtil {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    public static Instant convertToInstant(String timeString, ZoneId zoneId) {
        try {
            LocalTime localTime = LocalTime.parse(timeString, FORMATTER);
            LocalDate today = LocalDate.now(zoneId);
            return LocalDateTime.of(today, localTime).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format: " + timeString, e);
        }
    }
}