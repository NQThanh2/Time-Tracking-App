package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    private static final Locale VIETNAM = new Locale("vi", "VN");
    private static final DateTimeFormatter DATE_HEADER_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VIETNAM);
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MM/yyyy", VIETNAM);
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM", VIETNAM);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", VIETNAM);
    private static final DateTimeFormatter STORAGE_TIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private AttendanceStore attendanceStore;
    private TextView biometricStatusText;
    private TextView todayDateText;
    private TextView checkInValueText;
    private TextView checkOutValueText;
    private TextView totalTodayValueText;
    private TextView monthTitleText;
    private TextView monthTotalText;
    private TextView emptySummaryText;
    private LinearLayout monthlySummaryContainer;
    private Button checkInButton;
    private Button checkOutButton;
    private Button catchUpButton;
    private Button previousMonthButton;
    private Button nextMonthButton;
    private YearMonth visibleMonth = YearMonth.now();
    private boolean biometricReady;
    private boolean promptShowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        attendanceStore = new AttendanceStore(this);
        bindViews();
        checkInButton.setOnClickListener(v -> authenticateAndRecord(PunchAction.IN));
        checkOutButton.setOnClickListener(v -> authenticateAndRecord(PunchAction.OUT));
        catchUpButton.setOnClickListener(v -> showCatchUpDialog());
        previousMonthButton.setOnClickListener(v -> {
            visibleMonth = visibleMonth.minusMonths(1);
            renderDashboard();
        });
        nextMonthButton.setOnClickListener(v -> {
            YearMonth currentMonth = YearMonth.now();
            if (visibleMonth.isBefore(currentMonth)) {
                visibleMonth = visibleMonth.plusMonths(1);
                renderDashboard();
            }
        });

        updateBiometricStatus();
        renderDashboard();
    }

    private void bindViews() {
        biometricStatusText = findViewById(R.id.biometricStatusText);
        todayDateText = findViewById(R.id.todayDateText);
        checkInValueText = findViewById(R.id.checkInValueText);
        checkOutValueText = findViewById(R.id.checkOutValueText);
        totalTodayValueText = findViewById(R.id.totalTodayValueText);
        monthTitleText = findViewById(R.id.monthTitleText);
        monthTotalText = findViewById(R.id.monthTotalText);
        emptySummaryText = findViewById(R.id.emptySummaryText);
        monthlySummaryContainer = findViewById(R.id.monthlySummaryContainer);
        checkInButton = findViewById(R.id.checkInButton);
        checkOutButton = findViewById(R.id.checkOutButton);
        catchUpButton = findViewById(R.id.catchUpButton);
        previousMonthButton = findViewById(R.id.previousMonthButton);
        nextMonthButton = findViewById(R.id.nextMonthButton);
    }

    private void updateBiometricStatus() {
        BiometricManager biometricManager = getSystemService(BiometricManager.class);
        if (biometricManager == null) {
            biometricReady = false;
            biometricStatusText.setText("Thiết bị không hỗ trợ xác thực sinh trắc học.");
            return;
        }

        int result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        biometricReady = result == BiometricManager.BIOMETRIC_SUCCESS;
        switch (result) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                biometricStatusText.setText("Sẵn sàng quét vân tay để ghi nhận giờ vào và giờ ra.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                biometricStatusText.setText("Chưa đăng ký vân tay. Hãy thêm vân tay trong Cài đặt của thiết bị.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                biometricStatusText.setText("Thiết bị không có phần cứng quét vân tay phù hợp.");
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                biometricStatusText.setText("Cảm biến vân tay đang tạm thời không khả dụng.");
                break;
            default:
                biometricStatusText.setText("Không thể dùng xác thực vân tay trên thiết bị này.");
                break;
        }
    }

    private void authenticateAndRecord(PunchAction action) {
        String blockedMessage = getBlockedMessage(action);
        if (blockedMessage != null) {
            Toast.makeText(this, blockedMessage, Toast.LENGTH_SHORT).show();
            return;
        }

        authenticateWithBiometric(
                action == PunchAction.IN ? "Quét vân tay vào làm" : "Quét vân tay ra làm",
                "Xác thực để ghi nhận thời gian " + action.displayName.toLowerCase(VIETNAM),
                () -> {
                    LocalDateTime now = LocalDateTime.now();
                    attendanceStore.insert(action, now);
                    Toast.makeText(
                            MainActivity.this,
                            "Đã ghi nhận " + action.displayName.toLowerCase(VIETNAM) + " lúc " + formatTime(now),
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
    }

    private void authenticateWithBiometric(String title, String subtitle, Runnable onAuthenticated) {
        promptShowing = true;
        renderDashboard();

        CancellationSignal cancellationSignal = new CancellationSignal();
        BiometricPrompt biometricPrompt = new BiometricPrompt.Builder(this)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("Hủy", getMainExecutor(), (dialog, which) -> cancellationSignal.cancel())
                .build();

        biometricPrompt.authenticate(
                cancellationSignal,
                getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        promptShowing = false;
                        renderDashboard();
                        Toast.makeText(MainActivity.this, errString, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        Toast.makeText(MainActivity.this, "Vân tay không khớp, hãy thử lại.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        try {
                            onAuthenticated.run();
                        } finally {
                            promptShowing = false;
                            renderDashboard();
                        }
                    }
                }
        );
    }

    private void showCatchUpDialog() {
        if (!biometricReady) {
            Toast.makeText(this, "Thiết bị chưa sẵn sàng quét vân tay.", Toast.LENGTH_SHORT).show();
            return;
        }

        CatchUpDraft draft = new CatchUpDraft(LocalDate.now().minusDays(1));
        applySummaryToCatchUpDraft(draft);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(0, dp(8), 0, 0);

        Button dateButton = createDialogChoiceButton(formatCatchUpDate(draft.date));
        Button checkInTimeButton = createDialogChoiceButton(formatTime(draft.checkInTime));
        Button checkOutTimeButton = createDialogChoiceButton(formatTime(draft.checkOutTime));

        form.addView(createDialogRow("Ngày", dateButton));
        form.addView(createDialogRow("Giờ vào", checkInTimeButton));
        form.addView(createDialogRow("Giờ ra", checkOutTimeButton));

        dateButton.setOnClickListener(v -> showCatchUpDatePicker(
                draft,
                dateButton,
                checkInTimeButton,
                checkOutTimeButton
        ));
        checkInTimeButton.setOnClickListener(v -> showCatchUpTimePicker(
                "Giờ vào",
                draft.checkInTime,
                selectedTime -> {
                    draft.checkInTime = selectedTime;
                    checkInTimeButton.setText(formatTime(draft.checkInTime));
                }
        ));
        checkOutTimeButton.setOnClickListener(v -> showCatchUpTimePicker(
                "Giờ ra",
                draft.checkOutTime,
                selectedTime -> {
                    draft.checkOutTime = selectedTime;
                    checkOutTimeButton.setText(formatTime(draft.checkOutTime));
                }
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Điểm danh bù")
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null)
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (requestCatchUpAuthentication(draft)) {
                        dialog.dismiss();
                    }
                })
        );
        dialog.show();
    }

    private void showCatchUpDatePicker(
            CatchUpDraft draft,
            Button dateButton,
            Button checkInTimeButton,
            Button checkOutTimeButton
    ) {
        LocalDate latestCatchUpDate = LocalDate.now().minusDays(1);
        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    draft.date = LocalDate.of(year, month + 1, dayOfMonth);
                    applySummaryToCatchUpDraft(draft);
                    dateButton.setText(formatCatchUpDate(draft.date));
                    checkInTimeButton.setText(formatTime(draft.checkInTime));
                    checkOutTimeButton.setText(formatTime(draft.checkOutTime));
                },
                draft.date.getYear(),
                draft.date.getMonthValue() - 1,
                draft.date.getDayOfMonth()
        );
        picker.getDatePicker().setMaxDate(
                latestCatchUpDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        );
        picker.show();
    }

    private void showCatchUpTimePicker(String title, LocalTime currentTime, TimeSelectedListener listener) {
        TimePickerDialog picker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> listener.onTimeSelected(LocalTime.of(hourOfDay, minute)),
                currentTime.getHour(),
                currentTime.getMinute(),
                true
        );
        picker.setTitle(title);
        picker.show();
    }

    private boolean requestCatchUpAuthentication(CatchUpDraft draft) {
        LocalDate today = LocalDate.now();
        if (!draft.date.isBefore(today)) {
            Toast.makeText(this, "Chỉ có thể điểm danh bù cho ngày trước hôm nay.", Toast.LENGTH_SHORT).show();
            return false;
        }

        LocalDateTime checkIn = draft.date.atTime(normalizeTime(draft.checkInTime));
        LocalDateTime checkOut = draft.date.atTime(normalizeTime(draft.checkOutTime));
        if (!checkOut.isAfter(checkIn)) {
            Toast.makeText(this, "Giờ ra phải sau giờ vào.", Toast.LENGTH_SHORT).show();
            return false;
        }

        authenticateWithBiometric(
                "Xác thực điểm danh bù",
                "Lưu ngày " + formatCatchUpDate(draft.date),
                () -> {
                    attendanceStore.replaceDayWithPair(draft.date, checkIn, checkOut);
                    visibleMonth = YearMonth.from(draft.date);
                    Toast.makeText(
                            MainActivity.this,
                            "Đã lưu điểm danh bù ngày " + DAY_FORMAT.format(draft.date),
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
        return true;
    }

    private void applySummaryToCatchUpDraft(CatchUpDraft draft) {
        DailySummary summary = attendanceStore.getSummaryForDate(draft.date);
        draft.checkInTime = summary.firstIn != null
                ? normalizeTime(summary.firstIn.toLocalTime())
                : LocalTime.of(8, 0);
        draft.checkOutTime = summary.lastOut != null
                ? normalizeTime(summary.lastOut.toLocalTime())
                : LocalTime.of(17, 0);
    }

    private View createDialogRow(String label, Button valueButton) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView labelView = new TextView(this);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(dp(88), LinearLayout.LayoutParams.WRAP_CONTENT));
        labelView.setText(label);
        labelView.setTextSize(15);
        labelView.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray
        ));

        row.addView(labelView);
        row.addView(valueButton);
        return row;
    }

    private Button createDialogChoiceButton(String value) {
        Button button = new Button(this);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setText(value);
        return button;
    }

    private String getBlockedMessage(PunchAction action) {
        if (!biometricReady) {
            return "Thiết bị chưa sẵn sàng quét vân tay.";
        }

        DailySummary today = attendanceStore.getSummaryForDate(LocalDate.now());
        if (action == PunchAction.IN && today.firstIn != null) {
            return "Bạn đã chấm công vào hôm nay lúc " + formatTime(today.firstIn) + ".";
        }
        if (action == PunchAction.OUT && today.firstIn == null) {
            return "Bạn cần chấm công vào trước khi chấm công ra.";
        }
        if (action == PunchAction.OUT && today.lastOut != null) {
            return "Bạn đã chấm công ra hôm nay lúc " + formatTime(today.lastOut) + ".";
        }
        return null;
    }

    private void renderDashboard() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        if (visibleMonth == null || visibleMonth.isAfter(currentMonth)) {
            visibleMonth = currentMonth;
        }
        DailySummary todaySummary = attendanceStore.getSummaryForDate(today);

        todayDateText.setText(capitalize(DATE_HEADER_FORMAT.format(today)));
        checkInValueText.setText(formatTime(todaySummary.firstIn));
        checkOutValueText.setText(formatTime(todaySummary.lastOut));
        totalTodayValueText.setText(formatTodayTotal(todaySummary));

        checkInButton.setEnabled(!promptShowing && biometricReady && todaySummary.firstIn == null);
        checkOutButton.setEnabled(!promptShowing && biometricReady && todaySummary.firstIn != null && todaySummary.lastOut == null);
        catchUpButton.setEnabled(!promptShowing && biometricReady);
        previousMonthButton.setEnabled(!promptShowing);
        nextMonthButton.setEnabled(!promptShowing && visibleMonth.isBefore(currentMonth));

        renderMonthlySummary(visibleMonth);
    }

    private void renderMonthlySummary(YearMonth month) {
        Map<LocalDate, DailySummary> summaries = attendanceStore.getSummariesForMonth(month);
        LocalDate today = LocalDate.now();
        LocalDate lastVisibleDay = month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();

        monthTitleText.setText("Tổng hợp tháng " + MONTH_FORMAT.format(month.atDay(1)));
        monthTotalText.setText("Tổng tháng: " + formatDuration(calculateMonthTotalMinutes(summaries)));
        monthlySummaryContainer.removeAllViews();

        if (summaries.isEmpty()) {
            emptySummaryText.setVisibility(View.VISIBLE);
            return;
        }

        emptySummaryText.setVisibility(View.GONE);
        for (LocalDate date = month.atDay(1); !date.isAfter(lastVisibleDay); date = date.plusDays(1)) {
            DailySummary summary = summaries.get(date);
            monthlySummaryContainer.addView(createSummaryRow(summary != null ? summary : DailySummary.empty(date)));
            monthlySummaryContainer.addView(createDivider());
        }
    }

    private long calculateMonthTotalMinutes(Map<LocalDate, DailySummary> summaries) {
        long totalMinutes = 0;
        for (DailySummary summary : summaries.values()) {
            totalMinutes += summary.workedMinutes;
        }
        return totalMinutes;
    }

    private View createSummaryRow(DailySummary summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.addView(createCell(DAY_FORMAT.format(summary.date), 1.1f, Gravity.START, false));
        row.addView(createCell(formatTime(summary.firstIn), 0.9f, Gravity.CENTER, false));
        row.addView(createCell(formatTime(summary.lastOut), 0.9f, Gravity.CENTER, false));
        row.addView(createCell(formatStandardWorkDuration(summary), 0.9f, Gravity.END, false));
        row.addView(createCell(formatOvertimeDuration(summary), 0.9f, Gravity.END, false));
        row.addView(createCell(formatSummaryDuration(summary), 1.1f, Gravity.END, true));
        return row;
    }

    private TextView createCell(String value, float weight, int gravity, boolean strong) {
        TextView textView = new TextView(this);
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        textView.setGravity(gravity);
        textView.setText(value);
        textView.setTextSize(14);
        textView.setTextColor(resolveThemeColor(
                strong
                        ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorOnSurfaceVariant,
                strong ? android.R.color.black : android.R.color.darker_gray
        ));
        textView.setSingleLine(false);
        if (strong) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return textView;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        ));
        divider.setBackgroundColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOutlineVariant,
                android.R.color.darker_gray
        ));
        return divider;
    }

    private int resolveThemeColor(int attr, int fallbackColorRes) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return getColor(typedValue.resourceId);
            }
            return typedValue.data;
        }
        return getColor(fallbackColorRes);
    }

    private String formatTodayTotal(DailySummary summary) {
        if (summary.firstIn == null) {
            return "--";
        }
        if (summary.lastOut == null) {
            return "Đang làm";
        }
        return formatDuration(summary.workedMinutes);
    }

    private String formatSummaryDuration(DailySummary summary) {
        if (summary.firstIn != null && summary.lastOut == null) {
            return "Chưa ra";
        }
        if (summary.workedMinutes <= 0) {
            return "0 phút";
        }
        return formatDuration(summary.workedMinutes);
    }

    private String formatStandardWorkDuration(DailySummary summary) {
        if (summary.firstIn != null && summary.lastOut == null) {
            return "Chưa ra";
        }
        return formatDuration(AttendanceCalculator.standardWorkMinutes(summary.workedMinutes));
    }

    private String formatOvertimeDuration(DailySummary summary) {
        if (summary.firstIn != null && summary.lastOut == null) {
            return "Chưa ra";
        }
        return formatDuration(AttendanceCalculator.overtimeMinutes(summary.workedMinutes));
    }

    private String formatDuration(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours <= 0) {
            return minutes + " phút";
        }
        if (minutes == 0) {
            return hours + " giờ";
        }
        return hours + " giờ " + minutes + " phút";
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? "--" : TIME_FORMAT.format(dateTime);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "--" : TIME_FORMAT.format(time);
    }

    private String formatCatchUpDate(LocalDate date) {
        return capitalize(DATE_HEADER_FORMAT.format(date));
    }

    private LocalTime normalizeTime(LocalTime time) {
        return time.withSecond(0).withNano(0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(VIETNAM) + value.substring(1);
    }

    private interface TimeSelectedListener {
        void onTimeSelected(LocalTime selectedTime);
    }

    private static final class CatchUpDraft {
        private LocalDate date;
        private LocalTime checkInTime;
        private LocalTime checkOutTime;

        private CatchUpDraft(LocalDate date) {
            this.date = date;
            this.checkInTime = LocalTime.of(8, 0);
            this.checkOutTime = LocalTime.of(17, 0);
        }
    }

    private enum PunchAction {
        IN("IN", "Vào làm"),
        OUT("OUT", "Ra làm");

        private final String storageValue;
        private final String displayName;

        PunchAction(String storageValue, String displayName) {
            this.storageValue = storageValue;
            this.displayName = displayName;
        }

        private static PunchAction fromStorageValue(String value) {
            return OUT.storageValue.equals(value) ? OUT : IN;
        }
    }

    private static final class AttendanceEvent {
        private final PunchAction action;
        private final LocalDateTime timestamp;

        private AttendanceEvent(PunchAction action, LocalDateTime timestamp) {
            this.action = action;
            this.timestamp = timestamp;
        }
    }

    private static final class DailySummary {
        private final LocalDate date;
        private final LocalDateTime firstIn;
        private final LocalDateTime lastOut;
        private final long workedMinutes;

        private DailySummary(LocalDate date, LocalDateTime firstIn, LocalDateTime lastOut, long workedMinutes) {
            this.date = date;
            this.firstIn = firstIn;
            this.lastOut = lastOut;
            this.workedMinutes = workedMinutes;
        }

        private static DailySummary empty(LocalDate date) {
            return new DailySummary(date, null, null, 0);
        }
    }

    private static final class AttendanceStore extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "attendance.db";
        private static final int DATABASE_VERSION = 1;
        private static final String TABLE_EVENTS = "attendance_events";
        private static final String COLUMN_ID = "id";
        private static final String COLUMN_ACTION = "action";
        private static final String COLUMN_TIMESTAMP = "timestamp";
        private static final String COLUMN_WORK_DATE = "work_date";

        private AttendanceStore(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_EVENTS + " ("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COLUMN_ACTION + " TEXT NOT NULL, "
                    + COLUMN_TIMESTAMP + " TEXT NOT NULL, "
                    + COLUMN_WORK_DATE + " TEXT NOT NULL)");
            db.execSQL("CREATE INDEX idx_attendance_work_date ON " + TABLE_EVENTS + " (" + COLUMN_WORK_DATE + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_EVENTS);
            onCreate(db);
        }

        private void insert(PunchAction action, LocalDateTime timestamp) {
            insert(getWritableDatabase(), action, timestamp);
        }

        private void replaceDayWithPair(LocalDate date, LocalDateTime checkIn, LocalDateTime checkOut) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete(TABLE_EVENTS, COLUMN_WORK_DATE + " = ?", new String[]{date.toString()});
                insert(db, PunchAction.IN, checkIn);
                insert(db, PunchAction.OUT, checkOut);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private void insert(SQLiteDatabase db, PunchAction action, LocalDateTime timestamp) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_ACTION, action.storageValue);
            values.put(COLUMN_TIMESTAMP, STORAGE_TIME_FORMAT.format(timestamp));
            values.put(COLUMN_WORK_DATE, timestamp.toLocalDate().toString());
            db.insert(TABLE_EVENTS, null, values);
        }

        private DailySummary getSummaryForDate(LocalDate date) {
            List<AttendanceEvent> events = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor cursor = db.query(
                    TABLE_EVENTS,
                    new String[]{COLUMN_ACTION, COLUMN_TIMESTAMP},
                    COLUMN_WORK_DATE + " = ?",
                    new String[]{date.toString()},
                    null,
                    null,
                    COLUMN_TIMESTAMP + " ASC"
            )) {
                while (cursor.moveToNext()) {
                    events.add(readEvent(cursor));
                }
            }
            return summarize(date, events);
        }

        private Map<LocalDate, DailySummary> getSummariesForMonth(YearMonth month) {
            LocalDate firstDay = month.atDay(1);
            LocalDate nextMonth = month.plusMonths(1).atDay(1);
            Map<LocalDate, List<AttendanceEvent>> eventsByDate = new TreeMap<>();
            SQLiteDatabase db = getReadableDatabase();

            try (Cursor cursor = db.query(
                    TABLE_EVENTS,
                    new String[]{COLUMN_ACTION, COLUMN_TIMESTAMP, COLUMN_WORK_DATE},
                    COLUMN_WORK_DATE + " >= ? AND " + COLUMN_WORK_DATE + " < ?",
                    new String[]{firstDay.toString(), nextMonth.toString()},
                    null,
                    null,
                    COLUMN_TIMESTAMP + " ASC"
            )) {
                while (cursor.moveToNext()) {
                    LocalDate date = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORK_DATE)));
                    List<AttendanceEvent> events = eventsByDate.get(date);
                    if (events == null) {
                        events = new ArrayList<>();
                        eventsByDate.put(date, events);
                    }
                    events.add(readEvent(cursor));
                }
            }

            Map<LocalDate, DailySummary> summaries = new TreeMap<>();
            for (Map.Entry<LocalDate, List<AttendanceEvent>> entry : eventsByDate.entrySet()) {
                summaries.put(entry.getKey(), summarize(entry.getKey(), entry.getValue()));
            }
            return summaries;
        }

        private static AttendanceEvent readEvent(Cursor cursor) {
            PunchAction action = PunchAction.fromStorageValue(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACTION))
            );
            LocalDateTime timestamp = LocalDateTime.parse(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    STORAGE_TIME_FORMAT
            );
            return new AttendanceEvent(action, timestamp);
        }

        private static DailySummary summarize(LocalDate date, List<AttendanceEvent> events) {
            LocalDateTime firstIn = null;
            LocalDateTime lastOut = null;

            for (AttendanceEvent event : events) {
                if (event.action == PunchAction.IN && firstIn == null) {
                    firstIn = event.timestamp;
                } else if (event.action == PunchAction.OUT && firstIn != null && event.timestamp.isAfter(firstIn)) {
                    lastOut = event.timestamp;
                }
            }

            long workedMinutes = 0;
            if (firstIn != null && lastOut != null && lastOut.isAfter(firstIn)) {
                workedMinutes = AttendanceCalculator.workedMinutesRoundedDown(firstIn, lastOut);
            }

            return new DailySummary(date, firstIn, lastOut, workedMinutes);
        }
    }
}
