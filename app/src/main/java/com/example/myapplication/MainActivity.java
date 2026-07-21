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
import android.content.SharedPreferences;

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

    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_HOURLY_WAGE = "hourly_wage";
    private static final int PARKING_FEE_PER_TIME = 5000;

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
    private Button parkingButton;
    private Button parkingAddButton;
    private Button parkingMinusButton;
    private Button previousMonthButton;
    private Button nextMonthButton;
    private Button viewCalendarButton; // Nút lịch

    private com.google.android.material.textfield.TextInputEditText hourlyWageInput;
    private TextView monthlySalaryText;
    private TextView parkingFeeText;
    private long currentMonthTotalMinutes = 0;

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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedWage = prefs.getString(KEY_HOURLY_WAGE, "");
        if (!savedWage.isEmpty()) {
            hourlyWageInput.setText(savedWage);
        }

        hourlyWageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_HOURLY_WAGE, s.toString())
                        .apply();

                calculateAndDisplaySalary();
            }
        });

        checkInButton.setOnClickListener(v -> authenticateAndRecord(PunchAction.IN));
        checkOutButton.setOnClickListener(v -> authenticateAndRecord(PunchAction.OUT));
        catchUpButton.setOnClickListener(v -> showCatchUpDialog());

        parkingButton.setOnClickListener(v -> recordParking());
        parkingAddButton.setOnClickListener(v -> recordParking());
        parkingMinusButton.setOnClickListener(v -> undoParking());

        // Bắt sự kiện bấm vào nút xem lịch
        viewCalendarButton.setOnClickListener(v -> showCalendarDialog());

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
        parkingButton = findViewById(R.id.parkingButton);
        parkingAddButton = findViewById(R.id.parkingAddButton);
        parkingMinusButton = findViewById(R.id.parkingMinusButton);
        previousMonthButton = findViewById(R.id.previousMonthButton);
        nextMonthButton = findViewById(R.id.nextMonthButton);
        viewCalendarButton = findViewById(R.id.viewCalendarButton); // Ánh xạ nút lịch

        hourlyWageInput = findViewById(R.id.hourlyWageInput);
        monthlySalaryText = findViewById(R.id.monthlySalaryText);
        parkingFeeText = findViewById(R.id.parkingFeeText);
    }

    // MỚI: Các hàm để tạo và hiển thị Bảng Lịch (Heatmap Calendar)
    private void showCalendarDialog() {
        Map<LocalDate, DailySummary> summaries = attendanceStore.getSummariesForMonth(visibleMonth);
        LocalDate firstDay = visibleMonth.atDay(1);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        container.setPadding(padding, padding, padding, padding);

        // Tạo hàng tiêu đề các thứ trong tuần
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] daysOfWeek = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        for (String dow : daysOfWeek) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setGravity(Gravity.CENTER);
            tv.setText(dow);
            tv.setTextSize(13);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, android.R.color.darker_gray));
            headerRow.addView(tv);
        }
        container.addView(headerRow);

        // Vẽ lưới lịch
        int startDow = firstDay.getDayOfWeek().getValue(); // 1 = T2, 7 = CN
        int daysInMonth = visibleMonth.lengthOfMonth();

        LinearLayout currentRow = new LinearLayout(this);
        currentRow.setOrientation(LinearLayout.HORIZONTAL);
        currentRow.setPadding(0, dp(8), 0, 0);

        // Chèn các ô trống vào đầu tháng (nếu mùng 1 không phải T2)
        for (int i = 1; i < startDow; i++) {
            currentRow.addView(createCalendarCell(null, 0));
        }

        // Duyệt từng ngày trong tháng
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = visibleMonth.atDay(day);
            DailySummary summary = summaries.get(date);
            long minutes = summary != null ? summary.workedMinutes : 0;

            currentRow.addView(createCalendarCell(String.valueOf(day), minutes));

            // Hết 1 tuần (chủ nhật) hoặc là ngày cuối cùng của tháng -> Chuyển sang hàng mới
            if (date.getDayOfWeek().getValue() == 7 || day == daysInMonth) {
                // Nếu là ngày cuối tháng, thêm các ô trống để đủ 7 cột
                if (day == daysInMonth) {
                    int endDow = date.getDayOfWeek().getValue();
                    for (int i = endDow; i < 7; i++) {
                        currentRow.addView(createCalendarCell(null, 0));
                    }
                }
                container.addView(currentRow);
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setPadding(0, dp(8), 0, 0);
            }
        }

        // Thêm chú thích màu sắc
        container.addView(createLegendView());

        new AlertDialog.Builder(this)
                .setTitle("Lịch làm việc - Tháng " + MONTH_FORMAT.format(firstDay))
                .setView(container)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private View createCalendarCell(String text, long workedMinutes) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);

        if (text == null) {
            tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            return tv;
        }

        tv.setText(text);
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        // Logic chia màu (Heatmap)
        int bgColor;
        int textColor = android.graphics.Color.BLACK;

        if (workedMinutes == 0) {
            bgColor = android.graphics.Color.parseColor("#20888888"); // Xám nhạt (trong suốt 20%)
            textColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, android.R.color.black);
        } else if (workedMinutes <= 4 * 60) {
            bgColor = android.graphics.Color.parseColor("#90CAF9"); // Xanh dương nhạt (Làm ít)
        } else if (workedMinutes <= 8 * 60) {
            bgColor = android.graphics.Color.parseColor("#42A5F5"); // Xanh dương vừa (Làm chuẩn)
        } else {
            bgColor = android.graphics.Color.parseColor("#1565C0"); // Xanh dương đậm (Làm nhiều / Tăng ca)
            textColor = android.graphics.Color.WHITE;
        }

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(8));
        tv.setBackground(gd);
        tv.setTextColor(textColor);

        return tv;
    }

    private View createLegendView() {
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        legend.setPadding(0, dp(24), 0, 0);

        legend.addView(createLegendItem("#20888888", "Nghỉ"));
        legend.addView(createLegendItem("#90CAF9", "<4h"));
        legend.addView(createLegendItem("#42A5F5", "4-8h"));
        legend.addView(createLegendItem("#1565C0", ">8h"));

        return legend;
    }

    private View createLegendItem(String colorHex, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(4), 0, dp(8), 0);

        View colorBox = new View(this);
        colorBox.setLayoutParams(new LinearLayout.LayoutParams(dp(12), dp(12)));
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor(colorHex));
        gd.setCornerRadius(dp(2));
        colorBox.setBackground(gd);

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setText(label);
        tv.setTextSize(11);
        tv.setPadding(dp(4), 0, 0, 0);
        tv.setTextColor(resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY));

        item.addView(colorBox);
        item.addView(tv);
        return item;
    }
    // HẾT PHẦN LỊCH MỚI

    private String getMonthKey(YearMonth month) {
        return "parking_count_" + month.getYear() + "_" + month.getMonthValue();
    }

    private void recordParking() {
        String monthKey = getMonthKey(visibleMonth);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentCount = prefs.getInt(monthKey, 0);
        prefs.edit().putInt(monthKey, currentCount + 1).apply();

        Toast.makeText(this, "Đã cộng thêm 5.000đ tiền gửi xe", Toast.LENGTH_SHORT).show();
        updateParkingFeeDisplay();
    }

    private void undoParking() {
        String monthKey = getMonthKey(visibleMonth);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int currentCount = prefs.getInt(monthKey, 0);
        if (currentCount > 0) {
            prefs.edit().putInt(monthKey, currentCount - 1).apply();
            Toast.makeText(this, "Đã xóa 1 lần gửi xe", Toast.LENGTH_SHORT).show();
            updateParkingFeeDisplay();
        } else {
            Toast.makeText(this, "Chưa có lượt gửi xe nào trong tháng này để xóa", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateParkingFeeDisplay() {
        String monthKey = getMonthKey(visibleMonth);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int count = prefs.getInt(monthKey, 0);
        long totalParkingFee = (long) count * PARKING_FEE_PER_TIME;

        java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("vi", "VN"));
        parkingFeeText.setText("Gửi xe: " + formatter.format(totalParkingFee) + " (" + count + " lần)");
    }

    private void calculateAndDisplaySalary() {
        String wageStr = hourlyWageInput.getText() != null ? hourlyWageInput.getText().toString() : "";
        double hourlyWage = 0;

        if (!wageStr.isEmpty()) {
            try {
                hourlyWage = Double.parseDouble(wageStr);
            } catch (NumberFormatException e) {
                hourlyWage = 0;
            }
        }

        double hours = currentMonthTotalMinutes / 60.0;
        double totalSalary = hours * hourlyWage;

        java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("vi", "VN"));
        monthlySalaryText.setText(formatter.format(totalSalary));
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
        parkingButton.setEnabled(!promptShowing);
        parkingAddButton.setEnabled(!promptShowing);
        parkingMinusButton.setEnabled(!promptShowing);
        viewCalendarButton.setEnabled(!promptShowing);
        previousMonthButton.setEnabled(!promptShowing);
        nextMonthButton.setEnabled(!promptShowing && visibleMonth.isBefore(currentMonth));

        renderMonthlySummary(visibleMonth);
    }

    private void renderMonthlySummary(YearMonth month) {
        Map<LocalDate, DailySummary> summaries = attendanceStore.getSummariesForMonth(month);
        LocalDate today = LocalDate.now();
        LocalDate lastVisibleDay = month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();

        monthTitleText.setText("Tổng hợp tháng " + MONTH_FORMAT.format(month.atDay(1)));

        currentMonthTotalMinutes = calculateMonthTotalMinutes(summaries);
        monthTotalText.setText("Tổng tháng: " + formatDuration(currentMonthTotalMinutes));
        calculateAndDisplaySalary();
        updateParkingFeeDisplay();

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