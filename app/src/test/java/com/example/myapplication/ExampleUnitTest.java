package com.example.myapplication;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void roundDownToHalfHour_usesLowerThirtyMinuteBlock() {
        assertEquals(0, AttendanceCalculator.roundDownToHalfHour(29));
        assertEquals(30, AttendanceCalculator.roundDownToHalfHour(30));
        assertEquals(30, AttendanceCalculator.roundDownToHalfHour(59));
        assertEquals(60, AttendanceCalculator.roundDownToHalfHour(60));
        assertEquals(90, AttendanceCalculator.roundDownToHalfHour(119));
    }

    @Test
    public void workedMinutesRoundedDown_calculatesFromCheckInAndCheckOut() {
        java.time.LocalDateTime checkIn = java.time.LocalDateTime.of(2026, 6, 1, 8, 0);

        assertEquals(
                540,
                AttendanceCalculator.workedMinutesRoundedDown(
                        checkIn,
                        java.time.LocalDateTime.of(2026, 6, 1, 17, 29)
                )
        );
        assertEquals(
                570,
                AttendanceCalculator.workedMinutesRoundedDown(
                        checkIn,
                        java.time.LocalDateTime.of(2026, 6, 1, 17, 45)
                )
        );
    }

    @Test
    public void standardWorkAndOvertime_splitAtEightHours() {
        assertEquals(0, AttendanceCalculator.standardWorkMinutes(0));
        assertEquals(450, AttendanceCalculator.standardWorkMinutes(450));
        assertEquals(480, AttendanceCalculator.standardWorkMinutes(480));
        assertEquals(480, AttendanceCalculator.standardWorkMinutes(540));

        assertEquals(0, AttendanceCalculator.overtimeMinutes(450));
        assertEquals(0, AttendanceCalculator.overtimeMinutes(480));
        assertEquals(60, AttendanceCalculator.overtimeMinutes(540));
    }
}
