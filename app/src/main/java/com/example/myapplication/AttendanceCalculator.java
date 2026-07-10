package com.example.myapplication;

import java.time.Duration;
import java.time.LocalDateTime;

final class AttendanceCalculator {
    private static final long ROUNDING_BLOCK_MINUTES = 30;
    private static final long STANDARD_WORK_DAY_MINUTES = 8 * 60;

    private AttendanceCalculator() {
    }

    static long workedMinutesRoundedDown(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return 0;
        }
        return roundDownToHalfHour(Duration.between(start, end).toMinutes());
    }

    static long roundDownToHalfHour(long totalMinutes) {
        if (totalMinutes <= 0) {
            return 0;
        }
        return (totalMinutes / ROUNDING_BLOCK_MINUTES) * ROUNDING_BLOCK_MINUTES;
    }

    static long standardWorkMinutes(long totalMinutes) {
        if (totalMinutes <= 0) {
            return 0;
        }
        return Math.min(totalMinutes, STANDARD_WORK_DAY_MINUTES);
    }

    static long overtimeMinutes(long totalMinutes) {
        if (totalMinutes <= STANDARD_WORK_DAY_MINUTES) {
            return 0;
        }
        return totalMinutes - STANDARD_WORK_DAY_MINUTES;
    }
}
