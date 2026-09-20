package com.veap.performance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class ScheduleWaiter {
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ScheduleWaiter() {}

    public static void waitUntil(String start, ZoneId zone, long maximumWaitSeconds) throws InterruptedException {
        ZonedDateTime requested = LocalDateTime.parse(start, INPUT).atZone(zone);
        long seconds = Duration.between(ZonedDateTime.now(zone), requested).getSeconds();
        if (seconds < 0) {
            throw new IllegalArgumentException("La hora programada ya pasó");
        }
        if (seconds > maximumWaitSeconds) {
            throw new IllegalArgumentException(
                    "La hora programada no puede estar a más de " + maximumWaitSeconds + " segundos");
        }
        System.out.printf("La prueba comenzará en %d segundos (%s %s)%n", seconds, start, zone);
        Thread.sleep(seconds * 1000L);
    }
}

