package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ScheduleOcrParserTest {
    @Test
    public void filtersOnlyTheRequestedEmployeeAndReadsStartEndPairs() {
        List<ScheduleOcrParser.Token> tokens = new ArrayList<>();
        tokens.add(token("17-Aug", 260, 30, 340, 55));
        tokens.add(token("18-Aug", 460, 30, 540, 55));
        tokens.add(token("19-Aug", 660, 30, 740, 55));

        tokens.add(token("HOÀI", 60, 185, 125, 215));
        tokens.add(token("NAM", 135, 185, 195, 215));
        tokens.add(token("11", 285, 185, 310, 215));
        tokens.add(token("16", 325, 185, 350, 215));
        tokens.add(token("0", 490, 185, 510, 215));
        tokens.add(token("8.5", 680, 185, 715, 215));
        tokens.add(token("13", 725, 185, 750, 215));

        tokens.add(token("NGƯỜI", 50, 250, 130, 280));
        tokens.add(token("KHÁC", 140, 250, 205, 280));
        tokens.add(token("7", 285, 250, 310, 280));
        tokens.add(token("22", 325, 250, 350, 280));

        ScheduleOcrParser.Result result = ScheduleOcrParser.parse(
                tokens,
                "Hoài Nam",
                LocalDate.of(2026, 8, 18)
        );

        assertTrue(result.errorMessage, result.success);
        assertEquals("Hoài Nam", result.employeeName);
        assertEquals(2, result.shifts.size());
        assertShift(result.shifts.get(0), LocalDate.of(2026, 8, 17), 11, 0, 16, 0);
        assertShift(result.shifts.get(1), LocalDate.of(2026, 8, 19), 8, 30, 13, 0);
    }

    @Test
    public void reportsWhenTheEnteredNameIsNotInTheImage() {
        List<ScheduleOcrParser.Token> tokens = new ArrayList<>();
        tokens.add(token("17-Aug", 260, 30, 340, 55));
        tokens.add(token("HOÀI NAM", 60, 185, 195, 215));
        tokens.add(token("11", 285, 185, 310, 215));
        tokens.add(token("16", 325, 185, 350, 215));

        ScheduleOcrParser.Result result = ScheduleOcrParser.parse(
                tokens,
                "Nguyễn An",
                LocalDate.of(2026, 8, 18)
        );

        assertFalse(result.success);
        assertTrue(result.errorMessage.contains("Nguyễn An"));
    }

    @Test
    public void toleratesSmallOcrErrorsAndTwoTimesInOneCell() {
        List<ScheduleOcrParser.Token> tokens = new ArrayList<>();
        tokens.add(token("17-Aug", 260, 30, 340, 55));
        tokens.add(token("18-Au", 460, 30, 540, 55));
        tokens.add(token("19-Aug", 660, 30, 740, 55));
        tokens.add(token("20-Ang", 860, 30, 940, 55));
        tokens.add(token("21-Aug", 1060, 30, 1140, 55));
        tokens.add(token("22-Aug", 1260, 30, 1340, 55));
        tokens.add(token("HOINAM", 60, 185, 195, 215));
        tokens.add(token("11 16", 275, 185, 350, 215));
        tokens.add(token("0", 490, 185, 510, 215));
        tokens.add(token("16 20", 675, 185, 750, 215));
        tokens.add(token("0", 890, 185, 910, 215));
        tokens.add(token("14 20", 1075, 185, 1150, 215));
        tokens.add(token("0", 1290, 185, 1310, 215));
        tokens.add(token("9 14", 1475, 185, 1550, 215));

        ScheduleOcrParser.Result result = ScheduleOcrParser.parse(
                tokens,
                "Hoài Nam",
                LocalDate.of(2026, 8, 18)
        );

        assertTrue(result.errorMessage, result.success);
        assertEquals(4, result.shifts.size());
        assertShift(result.shifts.get(0), LocalDate.of(2026, 8, 17), 11, 0, 16, 0);
        assertShift(result.shifts.get(1), LocalDate.of(2026, 8, 19), 16, 0, 20, 0);
        assertShift(result.shifts.get(2), LocalDate.of(2026, 8, 21), 14, 0, 20, 0);
        assertShift(result.shifts.get(3), LocalDate.of(2026, 8, 23), 9, 0, 14, 0);
    }

    private static ScheduleOcrParser.Token token(
            String text,
            float left,
            float top,
            float right,
            float bottom
    ) {
        return new ScheduleOcrParser.Token(text, left, top, right, bottom);
    }

    private static void assertShift(
            WorkShift shift,
            LocalDate date,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        assertEquals(date, shift.date);
        assertEquals(LocalTime.of(startHour, startMinute), shift.start);
        assertEquals(LocalTime.of(endHour, endMinute), shift.end);
    }
}
