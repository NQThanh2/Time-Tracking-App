package com.example.myapplication;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScheduleOcrParser {
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^(\\d{1,2})\\s*[-/.]\\s*([A-Z]{2,9}\\d{0,2}|\\d{1,2})$"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?<![0-9])([0-9OIJL]{1,3})(?:[.,](\\d+))?(?![0-9])"
    );
    private static final Map<String, Integer> MONTHS = createMonthMap();

    private ScheduleOcrParser() {
    }

    static Result parse(List<Token> tokens, String employeeName, LocalDate referenceDate) {
        String normalizedName = normalize(employeeName);
        if (normalizedName.isEmpty()) {
            return Result.failure("Hãy nhập họ tên cần tìm.");
        }

        RowMatch rowMatch = findEmployeeRow(tokens, normalizedName);
        if (rowMatch == null) {
            return Result.failure("Không tìm thấy tên “" + employeeName.trim() + "” trong ảnh.");
        }

        List<DateColumn> columns = findDateColumns(tokens, referenceDate, rowMatch.centerY);
        if (columns.isEmpty()) {
            return Result.failure("Đã tìm thấy tên nhưng không đọc được các cột ngày.");
        }

        float searchTolerance = Math.max(32f, rowMatch.height * 3f);
        List<List<ShiftCandidate>> candidatesByColumn = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            DateColumn column = columns.get(index);
            float leftBoundary = index == 0
                    ? column.centerX - distanceToNext(columns, index) / 2f
                    : (columns.get(index - 1).centerX + column.centerX) / 2f;
            float rightBoundary = index == columns.size() - 1
                    ? column.centerX + distanceToPrevious(columns, index) / 2f
                    : (column.centerX + columns.get(index + 1).centerX) / 2f;

            List<TimeValue> values = new ArrayList<>();
            for (Token token : tokens) {
                if (token.centerX() < leftBoundary || token.centerX() >= rightBoundary) {
                    continue;
                }
                if (Math.abs(token.centerY() - rowMatch.centerY) > searchTolerance) {
                    continue;
                }
                for (TimeValue value : parseTimeValues(token)) {
                    if (value.decimalHours > 0 && value.decimalHours <= 24) {
                        values.add(value);
                    }
                }
            }
            values.sort(Comparator.comparingDouble(value -> value.centerX));
            values = removeDuplicateValues(values);
            candidatesByColumn.add(buildShiftCandidates(
                    values,
                    Math.max(35f, (rightBoundary - leftBoundary) * 0.55f),
                    Math.max(6f, rowMatch.height * 0.8f)
            ));
        }

        float rowSlope = findBestRowSlope(candidatesByColumn, rowMatch);
        float matchTolerance = Math.max(7f, rowMatch.height * 0.85f);
        List<WorkShift> shifts = new ArrayList<>();
        for (int index = 0; index < columns.size(); index++) {
            ShiftCandidate candidate = closestCandidateToRow(
                    candidatesByColumn.get(index),
                    rowMatch,
                    rowSlope,
                    matchTolerance
            );
            if (candidate == null) {
                continue;
            }
            LocalTime start = decimalHourToTime(candidate.startHours);
            LocalTime end = decimalHourToTime(candidate.endHours);
            if (start != null && end != null && end.isAfter(start)) {
                shifts.add(new WorkShift(columns.get(index).date, start, end));
            }
        }

        if (shifts.isEmpty()) {
            return Result.failure("Đã tìm thấy tên nhưng chưa đọc được cặp giờ vào–ra nào.");
        }
        shifts.sort(Comparator.comparing(shift -> shift.date));
        return Result.success(employeeName.trim(), shifts);
    }

    private static RowMatch findEmployeeRow(List<Token> tokens, String normalizedName) {
        String[] nameParts = normalizedName.split("\\s+");
        String compactName = normalizedName.replace(" ", "");
        for (Token token : tokens) {
            String normalizedToken = normalize(token.text);
            String compactToken = normalizedToken.replace(" ", "");
            if (normalizedToken.contains(normalizedName)
                    || isLikelyOcrNameMatch(compactToken, compactName)) {
                return new RowMatch(token.centerX(), token.centerY(), token.height());
            }
        }

        for (Token anchor : tokens) {
            float tolerance = Math.max(10f, anchor.height());
            List<Token> sameRow = new ArrayList<>();
            for (Token candidate : tokens) {
                if (Math.abs(candidate.centerY() - anchor.centerY()) <= tolerance) {
                    sameRow.add(candidate);
                }
            }
            sameRow.sort(Comparator.comparingDouble(Token::centerX));
            StringBuilder rowText = new StringBuilder();
            for (Token token : sameRow) {
                String normalized = normalize(token.text);
                if (!normalized.isEmpty()) {
                    if (rowText.length() > 0) {
                        rowText.append(' ');
                    }
                    rowText.append(normalized);
                }
            }
            if (containsNameParts(rowText.toString(), nameParts)) {
                return new RowMatch(anchor.centerX(), anchor.centerY(), anchor.height());
            }
        }
        return null;
    }

    private static boolean isLikelyOcrNameMatch(String candidate, String expected) {
        if (expected.length() < 5 || candidate.length() < 4
                || !candidate.matches("[A-Z]+")
                || !expected.matches("[A-Z]+")) {
            return false;
        }
        int allowedDistance = expected.length() >= 9 ? 2 : 1;
        if (Math.abs(candidate.length() - expected.length()) > allowedDistance) {
            return false;
        }
        return editDistance(candidate, expected) <= allowedDistance;
    }

    private static int editDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int column = 0; column <= second.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= first.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= second.length(); column++) {
                int substitutionCost = first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private static boolean containsNameParts(String rowText, String[] nameParts) {
        int searchFrom = 0;
        for (String part : nameParts) {
            int foundAt = rowText.indexOf(part, searchFrom);
            if (foundAt < 0) {
                return false;
            }
            searchFrom = foundAt + part.length();
        }
        return true;
    }

    private static List<DateColumn> findDateColumns(
            List<Token> tokens,
            LocalDate referenceDate,
            float employeeRowY
    ) {
        Map<LocalDate, DateColumn> uniqueColumns = new LinkedHashMap<>();
        for (Token token : tokens) {
            if (token.centerY() >= employeeRowY) {
                continue;
            }
            DateColumn column = parseDateColumn(token, referenceDate);
            if (column == null) {
                continue;
            }
            DateColumn existing = uniqueColumns.get(column.date);
            if (existing == null || token.centerY() > existing.sourceY) {
                uniqueColumns.put(column.date, column);
            }
        }
        List<DateColumn> columns = new ArrayList<>(uniqueColumns.values());
        columns.sort(Comparator.comparingDouble(column -> column.centerX));
        return completeWeeklyColumns(columns);
    }

    private static List<DateColumn> completeWeeklyColumns(List<DateColumn> detected) {
        if (detected.size() < 4 || detected.size() >= 7) {
            return detected;
        }
        DateColumn earliest = Collections.min(
                detected,
                Comparator.comparing(column -> column.date)
        );
        DateColumn latest = Collections.max(
                detected,
                Comparator.comparing(column -> column.date)
        );
        long detectedSpan = ChronoUnit.DAYS.between(earliest.date, latest.date);
        if (detectedSpan < 3 || detectedSpan > 6) {
            return detected;
        }

        LocalDate weekStart = earliest.date.minusDays(earliest.date.getDayOfWeek().getValue() - 1L);
        if (latest.date.isAfter(weekStart.plusDays(6))) {
            weekStart = earliest.date;
        }
        long anchorSpan = ChronoUnit.DAYS.between(earliest.date, latest.date);
        float daySpacing = (latest.centerX - earliest.centerX) / anchorSpan;
        if (Math.abs(daySpacing) < 5f) {
            return detected;
        }

        float headerY = 0f;
        for (DateColumn column : detected) {
            headerY += column.sourceY;
        }
        headerY /= detected.size();

        List<DateColumn> completed = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            long offsetFromAnchor = ChronoUnit.DAYS.between(earliest.date, date);
            completed.add(new DateColumn(
                    date,
                    earliest.centerX + daySpacing * offsetFromAnchor,
                    headerY
            ));
        }
        completed.sort(Comparator.comparingDouble(column -> column.centerX));
        return completed;
    }

    private static DateColumn parseDateColumn(Token token, LocalDate referenceDate) {
        String normalized = normalize(token.text).replace(" ", "");
        Matcher matcher = DATE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }
        int day;
        try {
            day = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }

        String monthToken = matcher.group(2);
        Integer month = parseMonth(monthToken);
        if (month == null) {
            try {
                month = Integer.parseInt(monthToken);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        LocalDate date = nearestDate(day, month, referenceDate);
        return date == null ? null : new DateColumn(date, token.centerX(), token.centerY());
    }

    private static Integer parseMonth(String token) {
        Integer exact = MONTHS.get(token);
        if (exact != null) {
            return exact;
        }
        if (token.length() < 2 || !token.matches("[A-Z]+")) {
            return null;
        }
        String[] english = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        Integer match = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < english.length; index++) {
            String month = english[index];
            int distance = month.startsWith(token) ? 0 : editDistance(token, month);
            if (distance < bestDistance) {
                match = index + 1;
                bestDistance = distance;
            } else if (distance == bestDistance) {
                match = null;
            }
        }
        return bestDistance <= 1 ? match : null;
    }

    private static LocalDate nearestDate(int day, int month, LocalDate referenceDate) {
        LocalDate closest = null;
        long closestDistance = Long.MAX_VALUE;
        for (int year = referenceDate.getYear() - 1; year <= referenceDate.getYear() + 1; year++) {
            try {
                LocalDate candidate = LocalDate.of(year, month, day);
                long distance = Math.abs(ChronoUnit.DAYS.between(referenceDate, candidate));
                if (distance < closestDistance) {
                    closest = candidate;
                    closestDistance = distance;
                }
            } catch (DateTimeException ignored) {
                // Ignore invalid day/month combinations from OCR.
            }
        }
        return closest;
    }

    private static List<TimeValue> parseTimeValues(Token token) {
        List<TimeValue> values = new ArrayList<>();
        String source = token.text.toUpperCase(Locale.ROOT);
        Matcher matcher = TIME_PATTERN.matcher(source);
        while (matcher.find()) {
            try {
                String integerText = matcher.group(1)
                        .replace('O', '0')
                        .replace('I', '1')
                        .replace('J', '1')
                        .replace('L', '1');
                int integerValue = Integer.parseInt(integerText);
                String fractionText = matcher.group(2);
                double value = integerValue;
                if (fractionText != null) {
                    value += Double.parseDouble("0." + fractionText);
                } else if (integerValue > 24 && integerValue <= 245
                        && integerValue % 10 == 5) {
                    value = integerValue / 10.0;
                }
                float relativeCenter = (matcher.start() + matcher.end()) / 2f
                        / Math.max(1, source.length());
                float centerX = token.left + (token.right - token.left) * relativeCenter;
                values.add(new TimeValue(value, centerX, token.centerY()));
            } catch (NumberFormatException ignored) {
                // Ignore a malformed number while preserving other values in the cell.
            }
        }
        return values;
    }

    private static List<ShiftCandidate> buildShiftCandidates(
            List<TimeValue> values,
            float maximumHorizontalDistance,
            float maximumVerticalDistance
    ) {
        List<ShiftCandidate> candidates = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < values.size(); firstIndex++) {
            TimeValue first = values.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < values.size(); secondIndex++) {
                TimeValue second = values.get(secondIndex);
                float horizontalDistance = second.centerX - first.centerX;
                if (horizontalDistance <= 2f || horizontalDistance > maximumHorizontalDistance) {
                    continue;
                }
                if (Math.abs(first.centerY - second.centerY) > maximumVerticalDistance) {
                    continue;
                }
                if (second.decimalHours <= first.decimalHours) {
                    continue;
                }
                candidates.add(new ShiftCandidate(
                        first.decimalHours,
                        second.decimalHours,
                        (first.centerX + second.centerX) / 2f,
                        (first.centerY + second.centerY) / 2f
                ));
            }
        }
        return candidates;
    }

    private static float findBestRowSlope(
            List<List<ShiftCandidate>> candidatesByColumn,
            RowMatch rowMatch
    ) {
        List<Float> possibleSlopes = new ArrayList<>();
        possibleSlopes.add(0f);
        for (List<ShiftCandidate> candidates : candidatesByColumn) {
            for (ShiftCandidate candidate : candidates) {
                float horizontalDistance = candidate.centerX - rowMatch.centerX;
                if (Math.abs(horizontalDistance) < 20f) {
                    continue;
                }
                float slope = (candidate.centerY - rowMatch.centerY) / horizontalDistance;
                if (Math.abs(slope) <= 0.09f) {
                    possibleSlopes.add(slope);
                }
            }
        }

        float matchTolerance = Math.max(7f, rowMatch.height * 0.85f);
        float bestSlope = 0f;
        int bestMatches = -1;
        float bestResidual = Float.MAX_VALUE;
        for (float slope : possibleSlopes) {
            int matches = 0;
            float residual = 0f;
            for (List<ShiftCandidate> candidates : candidatesByColumn) {
                float closest = Float.MAX_VALUE;
                for (ShiftCandidate candidate : candidates) {
                    float predictedY = rowMatch.centerY
                            + slope * (candidate.centerX - rowMatch.centerX);
                    closest = Math.min(closest, Math.abs(candidate.centerY - predictedY));
                }
                if (closest <= matchTolerance) {
                    matches++;
                    residual += closest;
                }
            }
            if (matches > bestMatches
                    || (matches == bestMatches && residual < bestResidual)) {
                bestMatches = matches;
                bestResidual = residual;
                bestSlope = slope;
            }
        }
        return bestSlope;
    }

    private static ShiftCandidate closestCandidateToRow(
            List<ShiftCandidate> candidates,
            RowMatch rowMatch,
            float slope,
            float tolerance
    ) {
        ShiftCandidate closestCandidate = null;
        float closestDistance = Float.MAX_VALUE;
        for (ShiftCandidate candidate : candidates) {
            float predictedY = rowMatch.centerY + slope * (candidate.centerX - rowMatch.centerX);
            float distance = Math.abs(candidate.centerY - predictedY);
            if (distance <= tolerance && distance < closestDistance) {
                closestCandidate = candidate;
                closestDistance = distance;
            }
        }
        return closestCandidate;
    }

    private static List<TimeValue> removeDuplicateValues(List<TimeValue> values) {
        if (values.size() < 2) {
            return values;
        }
        List<TimeValue> deduplicated = new ArrayList<>();
        for (TimeValue value : values) {
            boolean duplicate = false;
            for (TimeValue existing : deduplicated) {
                if (Math.abs(existing.centerX - value.centerX) < 3f
                        && Math.abs(existing.decimalHours - value.decimalHours) < 0.01) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduplicated.add(value);
            }
        }
        return deduplicated;
    }

    private static LocalTime decimalHourToTime(double value) {
        int hours = (int) Math.floor(value);
        int minutes = (int) Math.round((value - hours) * 60);
        if (minutes == 60) {
            hours++;
            minutes = 0;
        }
        if (hours == 24 && minutes == 0) {
            return LocalTime.MAX.withSecond(0).withNano(0);
        }
        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
            return null;
        }
        return LocalTime.of(hours, minutes);
    }

    private static float distanceToNext(List<DateColumn> columns, int index) {
        return index + 1 < columns.size()
                ? columns.get(index + 1).centerX - columns.get(index).centerX
                : distanceToPrevious(columns, index);
    }

    private static float distanceToPrevious(List<DateColumn> columns, int index) {
        return index > 0
                ? columns.get(index).centerX - columns.get(index - 1).centerX
                : 100f;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('Đ', 'D')
                .replace('đ', 'd');
        return withoutMarks
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9.,/-]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static Map<String, Integer> createMonthMap() {
        Map<String, Integer> months = new HashMap<>();
        String[] english = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        for (int index = 0; index < english.length; index++) {
            months.put(english[index], index + 1);
        }
        months.put("THG1", 1);
        months.put("THG2", 2);
        months.put("THG3", 3);
        months.put("THG4", 4);
        months.put("THG5", 5);
        months.put("THG6", 6);
        months.put("THG7", 7);
        months.put("THG8", 8);
        months.put("THG9", 9);
        months.put("THG10", 10);
        months.put("THG11", 11);
        months.put("THG12", 12);
        return Collections.unmodifiableMap(months);
    }

    static final class Token {
        final String text;
        final float left;
        final float top;
        final float right;
        final float bottom;

        Token(String text, float left, float top, float right, float bottom) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float centerX() {
            return (left + right) / 2f;
        }

        float centerY() {
            return (top + bottom) / 2f;
        }

        float height() {
            return Math.max(1f, bottom - top);
        }
    }

    static final class Result {
        final boolean success;
        final String employeeName;
        final List<WorkShift> shifts;
        final String errorMessage;

        private Result(boolean success, String employeeName, List<WorkShift> shifts, String errorMessage) {
            this.success = success;
            this.employeeName = employeeName;
            this.shifts = shifts;
            this.errorMessage = errorMessage;
        }

        static Result success(String employeeName, List<WorkShift> shifts) {
            return new Result(true, employeeName, Collections.unmodifiableList(shifts), null);
        }

        static Result failure(String message) {
            return new Result(false, null, Collections.emptyList(), message);
        }
    }

    private static final class RowMatch {
        final float centerX;
        final float centerY;
        final float height;

        RowMatch(float centerX, float centerY, float height) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.height = height;
        }
    }

    private static final class DateColumn {
        final LocalDate date;
        final float centerX;
        final float sourceY;

        DateColumn(LocalDate date, float centerX, float sourceY) {
            this.date = date;
            this.centerX = centerX;
            this.sourceY = sourceY;
        }
    }

    private static final class TimeValue {
        final double decimalHours;
        final float centerX;
        final float centerY;

        TimeValue(double decimalHours, float centerX, float centerY) {
            this.decimalHours = decimalHours;
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }

    private static final class ShiftCandidate {
        final double startHours;
        final double endHours;
        final float centerX;
        final float centerY;

        ShiftCandidate(double startHours, double endHours, float centerX, float centerY) {
            this.startHours = startHours;
            this.endHours = endHours;
            this.centerX = centerX;
            this.centerY = centerY;
        }
    }
}
