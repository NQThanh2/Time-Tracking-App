package com.example.myapplication;

import java.time.LocalDate;
import java.time.LocalTime;

final class WorkShift {
    final LocalDate date;
    final LocalTime start;
    final LocalTime end;

    WorkShift(LocalDate date, LocalTime start, LocalTime end) {
        this.date = date;
        this.start = start;
        this.end = end;
    }
}
