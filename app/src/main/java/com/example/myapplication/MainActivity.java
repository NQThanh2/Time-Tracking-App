package com.example.myapplication;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    private static final DateTimeFormatter SUMMARY_DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd/MM", VIETNAM);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", VIETNAM);
    private static final DateTimeFormatter STORAGE_TIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_HOURLY_WAGE = "hourly_wage";
    private static final String KEY_BACKGROUND_URI = "background_image_uri";
    private static final int PARKING_FEE_PER_TIME = 5000;
    private static final float DIALOG_DIM_AMOUNT = 0.22f;
    private static final float BACKGROUND_BLUR_RADIUS_DP = 18f;

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
    private NestedScrollView daySummaryScroll;
    private Button checkInButton;
    private Button checkOutButton;
    private Button catchUpButton;
    private Button parkingButton;
    private Button parkingAddButton;
    private Button parkingMinusButton;
    private Button previousMonthButton;
    private Button nextMonthButton;
    private Button viewCalendarButton; // Nút lịch
    private Button backgroundButton;
    private ImageView backgroundImageView;
    private View backgroundScrim;

    private com.google.android.material.textfield.TextInputEditText hourlyWageInput;
    private TextView monthlySalaryText;
    private TextView parkingFeeText;
    private long currentMonthTotalMinutes = 0;

    private YearMonth visibleMonth = YearMonth.now();
    private boolean biometricReady;
    private boolean promptShowing;
    private float summaryTouchDownX;
    private float summaryTouchDownY;

    private final ActivityResultLauncher<String[]> backgroundImagePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onBackgroundImageSelected);

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
        restoreBackgroundImage();
        confineDaySummaryScrolling();

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
        backgroundButton.setOnClickListener(v -> showBackgroundOptions());

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
        if (savedInstanceState == null) {
            animateDashboardEntrance();
        }
    }

    private void animateDashboardEntrance() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        int[] cardIds = {R.id.todayCard, R.id.monthCard, R.id.summaryFrame};
        for (int index = 0; index < cardIds.length; index++) {
            View card = findViewById(cardIds[index]);
            card.setAlpha(0f);
            card.setTranslationY(dp(18));
            card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 70L)
                    .setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void bindViews() {
        biometricStatusText = findViewById(R.id.biometricStatusText);
        todayDateText = findViewById(R.id.todayDateText);
        checkInValueText = findViewById(R.id.checkInValueText);
        checkOutValueText = findViewById(R.id.checkOutValueText);
        totalTodayValueText = findViewById(R.id.totalTodayValueText);
        monthTitleText = findViewById(R.id.monthTitleText);
        monthTotalText = findViewById(R.id.monthTotalText);
        monthlySummaryContainer = findViewById(R.id.monthlySummaryContainer);
        emptySummaryText = createEmptySummaryView();
        daySummaryScroll = findViewById(R.id.daySummaryScroll);
        checkInButton = findViewById(R.id.checkInButton);
        checkOutButton = findViewById(R.id.checkOutButton);
        catchUpButton = findViewById(R.id.catchUpButton);
        parkingButton = findViewById(R.id.parkingButton);
        parkingAddButton = findViewById(R.id.parkingAddButton);
        parkingMinusButton = findViewById(R.id.parkingMinusButton);
        previousMonthButton = findViewById(R.id.previousMonthButton);
        nextMonthButton = findViewById(R.id.nextMonthButton);
        viewCalendarButton = findViewById(R.id.viewCalendarButton); // Ánh xạ nút lịch
        backgroundButton = findViewById(R.id.backgroundButton);
        backgroundImageView = findViewById(R.id.backgroundImageView);
        backgroundScrim = findViewById(R.id.backgroundScrim);

        hourlyWageInput = findViewById(R.id.hourlyWageInput);
        monthlySalaryText = findViewById(R.id.monthlySalaryText);
        parkingFeeText = findViewById(R.id.parkingFeeText);
    }

    private void showBackgroundOptions() {
        String savedUri = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BACKGROUND_URI, "");
        if (savedUri == null || savedUri.isEmpty()) {
            openBackgroundPicker();
            return;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Tùy chỉnh ảnh nền")
                .setItems(new String[]{"Chọn ảnh khác", "Dùng nền mặc định"}, (ignored, which) -> {
                    if (which == 0) {
                        openBackgroundPicker();
                    } else {
                        clearBackgroundImage(true);
                    }
                })
                .setNegativeButton("Đóng", null)
                .create();
        showDialogWithBlur(dialog);
    }

    private void openBackgroundPicker() {
        backgroundImagePicker.launch(new String[]{"image/*"});
    }

    private void onBackgroundImageSelected(Uri uri) {
        if (uri == null) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // Một số trình chọn ảnh tự duy trì quyền đọc và không cấp cờ persistable.
        }

        try {
            applyBackgroundImage(uri);
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_BACKGROUND_URI, uri.toString())
                    .apply();
            Toast.makeText(this, "Đã cập nhật ảnh nền", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            clearBackgroundImage(false);
            Toast.makeText(this, "Không thể mở ảnh này, hãy chọn ảnh khác", Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreBackgroundImage() {
        String savedUri = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BACKGROUND_URI, "");
        if (savedUri == null || savedUri.isEmpty()) {
            clearBackgroundImage(false);
            return;
        }

        try {
            applyBackgroundImage(Uri.parse(savedUri));
        } catch (RuntimeException exception) {
            clearBackgroundImage(false);
        }
    }

    private void applyBackgroundImage(Uri uri) {
        backgroundImageView.setImageURI(null);
        backgroundImageView.setImageURI(uri);
        if (backgroundImageView.getDrawable() == null) {
            throw new IllegalArgumentException("Không đọc được ảnh nền");
        }
        backgroundImageView.setScaleX(1.08f);
        backgroundImageView.setScaleY(1.08f);
        backgroundImageView.setVisibility(View.VISIBLE);
        backgroundScrim.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImageView.setRenderEffect(RenderEffect.createBlurEffect(
                    dp((int) BACKGROUND_BLUR_RADIUS_DP),
                    dp((int) BACKGROUND_BLUR_RADIUS_DP),
                    Shader.TileMode.CLAMP
            ));
        }
    }

    private void clearBackgroundImage(boolean showConfirmation) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_BACKGROUND_URI)
                .apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundImageView.setRenderEffect(null);
        }
        backgroundImageView.setImageDrawable(null);
        backgroundImageView.setVisibility(View.GONE);
        backgroundScrim.setVisibility(View.GONE);
        if (showConfirmation) {
            Toast.makeText(this, "Đã dùng lại nền mặc định", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView createEmptySummaryView() {
        TextView emptyView = new TextView(this);
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280)
        ));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(24), dp(24), dp(24), dp(24));
        emptyView.setText(R.string.no_attendance_data);
        emptyView.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray
        ));
        emptyView.setTextSize(13);
        return emptyView;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void confineDaySummaryScrolling() {
        // Configure this after inflation. Setting android:nestedScrollingEnabled in XML
        // can call into NestedScrollView before its helper has been initialized.
        daySummaryScroll.setNestedScrollingEnabled(false);
        daySummaryScroll.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    summaryTouchDownX = event.getX();
                    summaryTouchDownY = event.getY();
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float distanceX = Math.abs(event.getX() - summaryTouchDownX);
                    float distanceY = Math.abs(event.getY() - summaryTouchDownY);
                    // Vertical gestures stay inside the day frame. Horizontal gestures are
                    // handed to the surrounding table so all columns remain reachable.
                    view.getParent().requestDisallowInterceptTouchEvent(distanceY >= distanceX);
                    break;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(event.getX() - summaryTouchDownX) < dp(4)
                            && Math.abs(event.getY() - summaryTouchDownY) < dp(4)) {
                        view.performClick();
                    }
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void showCalendarDialog() {
        Map<LocalDate, DailySummary> summaries = attendanceStore.getSummariesForMonth(visibleMonth);
        LocalDate firstDay = visibleMonth.atDay(1);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(8), dp(4), dp(8), dp(6));
        container.addView(createCalendarOverview(summaries));

        MaterialCardView calendarSurface = new MaterialCardView(this);
        LinearLayout.LayoutParams calendarSurfaceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        calendarSurfaceParams.topMargin = dp(12);
        calendarSurface.setLayoutParams(calendarSurfaceParams);
        calendarSurface.setRadius(dp(20));
        calendarSurface.setCardElevation(0);
        calendarSurface.setStrokeWidth(dp(1));
        calendarSurface.setStrokeColor(getColor(R.color.attendance_glass_stroke));
        calendarSurface.setCardBackgroundColor(getColor(R.color.attendance_glass_surface_strong));
        calendarSurface.setContentPadding(dp(8), dp(12), dp(8), dp(10));

        LinearLayout calendarContent = new LinearLayout(this);
        calendarContent.setOrientation(LinearLayout.VERTICAL);

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
        calendarContent.addView(headerRow);

        int startDow = firstDay.getDayOfWeek().getValue();
        int daysInMonth = visibleMonth.lengthOfMonth();

        LinearLayout currentRow = new LinearLayout(this);
        currentRow.setOrientation(LinearLayout.HORIZONTAL);
        currentRow.setPadding(0, dp(6), 0, 0);

        for (int i = 1; i < startDow; i++) {
            currentRow.addView(createCalendarCell(null, 0));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = visibleMonth.atDay(day);
            DailySummary summary = summaries.get(date);
            long minutes = summary != null ? summary.workedMinutes : 0;

            currentRow.addView(createCalendarCell(date, minutes));

            if (date.getDayOfWeek().getValue() == 7 || day == daysInMonth) {
                if (day == daysInMonth) {
                    int endDow = date.getDayOfWeek().getValue();
                    for (int i = endDow; i < 7; i++) {
                        currentRow.addView(createCalendarCell(null, 0));
                    }
                }
                calendarContent.addView(currentRow);
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setPadding(0, dp(6), 0, 0);
            }
        }

        calendarSurface.addView(calendarContent);
        container.addView(calendarSurface);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Lịch làm việc • " + MONTH_FORMAT.format(firstDay))
                .setView(container)
                .setPositiveButton("Đóng", null)
                .create();
        showDialogWithBlur(dialog);
    }

    private View createCalendarOverview(Map<LocalDate, DailySummary> summaries) {
        long totalMinutes = calculateMonthTotalMinutes(summaries);
        int workedDays = 0;
        for (DailySummary summary : summaries.values()) {
            if (summary.workedMinutes > 0) {
                workedDays++;
            }
        }

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.attendance_glass_stroke));
        card.setCardBackgroundColor(getColor(R.color.attendance_glass_surface));
        card.setContentPadding(dp(16), dp(13), dp(16), dp(13));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Tổng thời gian trong tháng");
        title.setTextSize(12);
        title.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray
        ));
        labels.addView(title);

        TextView total = new TextView(this);
        total.setText(formatDuration(totalMinutes));
        total.setTextSize(20);
        total.setTypeface(total.getTypeface(), android.graphics.Typeface.BOLD);
        total.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurface,
                android.R.color.black
        ));
        labels.addView(total);

        TextView days = new TextView(this);
        days.setText(workedDays + " ngày đi làm");
        days.setTextSize(12);
        days.setTextColor(resolveThemeColor(
                android.R.attr.colorAccent,
                android.R.color.holo_green_dark
        ));
        days.setTypeface(days.getTypeface(), android.graphics.Typeface.BOLD);

        content.addView(labels);
        content.addView(days);
        card.addView(content);
        return card;
    }

    private View createCalendarCell(LocalDate date, long workedMinutes) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);

        if (date == null) {
            tv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            return tv;
        }

        tv.setText(String.valueOf(date.getDayOfMonth()));
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setContentDescription(formatCatchUpDate(date) + ", " + formatDuration(workedMinutes));

        int bgColor;
        int textColor = android.graphics.Color.BLACK;

        if (workedMinutes == 0) {
            bgColor = android.graphics.Color.parseColor("#20888888");
            textColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, android.R.color.black);
        } else if (workedMinutes <= 4 * 60) {
            bgColor = android.graphics.Color.parseColor("#9FD7F7");
        } else if (workedMinutes <= 8 * 60) {
            bgColor = android.graphics.Color.parseColor("#4A9EE8");
        } else {
            bgColor = android.graphics.Color.parseColor("#1769AA");
            textColor = android.graphics.Color.WHITE;
        }

        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(12));
        if (date.equals(LocalDate.now())) {
            gd.setStroke(dp(2), resolveThemeColor(
                    android.R.attr.colorAccent,
                    android.R.color.holo_green_dark
            ));
        }
        tv.setBackground(gd);
        tv.setTextColor(textColor);

        return tv;
    }

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

        CatchUpDraft draft = new CatchUpDraft(LocalDate.now());
        applySummaryToCatchUpDraft(draft);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(6), dp(4), dp(6), dp(2));
        form.addView(createCatchUpIntro());

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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
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
        showDialogWithBlur(dialog);
    }

    private void showDialogWithBlur(AlertDialog dialog) {
        View activityContent = findViewById(android.R.id.content);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activityContent.setRenderEffect(RenderEffect.createBlurEffect(
                    dp(12),
                    dp(12),
                    Shader.TileMode.CLAMP
            ));
        }

        dialog.setOnDismissListener(ignored -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activityContent.setRenderEffect(null);
            }
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = DIALOG_DIM_AMOUNT;
            window.setAttributes(attributes);
        }
    }

    private void showCatchUpDatePicker(
            CatchUpDraft draft,
            Button dateButton,
            Button checkInTimeButton,
            Button checkOutTimeButton
    ) {
        LocalDate latestCatchUpDate = LocalDate.now();
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
        if (draft.date.isAfter(today)) {
            Toast.makeText(this, "Chỉ có thể điểm danh bù cho hôm nay hoặc ngày trước đó.", Toast.LENGTH_SHORT).show();
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

    private View createCatchUpIntro() {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);
        card.setRadius(dp(18));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.attendance_glass_stroke));
        card.setCardBackgroundColor(getColor(R.color.attendance_glass_surface));
        card.setContentPadding(dp(15), dp(13), dp(15), dp(13));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("Bổ sung ngày chấm công còn thiếu");
        title.setTextSize(15);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurface,
                android.R.color.black
        ));
        content.addView(title);

        TextView description = new TextView(this);
        description.setText("Chọn hôm nay hoặc ngày trước đó, giờ vào và giờ ra. Bạn sẽ xác thực vân tay trước khi lưu.");
        description.setPadding(0, dp(4), 0, 0);
        description.setTextSize(12);
        description.setLineSpacing(0, 1.08f);
        description.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray
        ));
        content.addView(description);
        card.addView(content);
        return card;
    }

    private View createDialogRow(String label, Button valueButton) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(4), 0, dp(4));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(18));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.attendance_glass_stroke));
        card.setCardBackgroundColor(getColor(R.color.attendance_glass_surface_strong));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(10), dp(10));

        TextView labelView = new TextView(this);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT));
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
        labelView.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray
        ));

        row.addView(labelView);
        row.addView(valueButton);
        card.addView(row);
        return card;
    }

    private Button createDialogChoiceButton(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setLayoutParams(new LinearLayout.LayoutParams(0, dp(46), 1));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setText(value);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(14));
        button.setStrokeWidth(dp(1));
        button.setStrokeColor(ColorStateList.valueOf(getColor(R.color.attendance_glass_stroke)));
        button.setBackgroundTintList(ColorStateList.valueOf(resolveThemeColor(
                com.google.android.material.R.attr.colorPrimaryContainer,
                android.R.color.white
        )));
        button.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                android.R.color.black
        ));
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
        backgroundButton.setEnabled(!promptShowing);
        previousMonthButton.setEnabled(!promptShowing);
        nextMonthButton.setEnabled(!promptShowing && visibleMonth.isBefore(currentMonth));

        renderMonthlySummary(visibleMonth);
    }

    private void renderMonthlySummary(YearMonth month) {
        Map<LocalDate, DailySummary> summaries = attendanceStore.getSummariesForMonth(month);
        LocalDate today = LocalDate.now();
        LocalDate lastVisibleDay = month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();

        monthTitleText.setText("Tháng " + MONTH_FORMAT.format(month.atDay(1)));

        currentMonthTotalMinutes = calculateMonthTotalMinutes(summaries);
        monthTotalText.setText("Tổng tháng: " + formatDuration(currentMonthTotalMinutes));
        calculateAndDisplaySalary();
        updateParkingFeeDisplay();

        monthlySummaryContainer.removeAllViews();
        daySummaryScroll.scrollTo(0, 0);

        if (summaries.isEmpty()) {
            emptySummaryText.setVisibility(View.VISIBLE);
            monthlySummaryContainer.addView(emptySummaryText);
            return;
        }

        emptySummaryText.setVisibility(View.GONE);
        for (LocalDate date = month.atDay(1); !date.isAfter(lastVisibleDay); date = date.plusDays(1)) {
            DailySummary summary = summaries.get(date);
            monthlySummaryContainer.addView(createSummaryRow(summary != null ? summary : DailySummary.empty(date)));
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
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(5), 0, dp(5));
        row.setLayoutParams(rowParams);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackgroundResource(R.drawable.bg_summary_row);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dateText = new TextView(this);
        dateText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        dateText.setText(capitalize(SUMMARY_DAY_FORMAT.format(summary.date)));
        dateText.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurface,
                android.R.color.black
        ));
        dateText.setTextSize(14);
        dateText.setTypeface(dateText.getTypeface(), android.graphics.Typeface.BOLD);
        titleRow.addView(dateText);

        TextView totalText = new TextView(this);
        totalText.setText(formatSummaryDuration(summary));
        totalText.setTextColor(resolveThemeColor(
                android.R.attr.colorAccent,
                android.R.color.holo_green_dark
        ));
        totalText.setTextSize(14);
        totalText.setTypeface(totalText.getTypeface(), android.graphics.Typeface.BOLD);
        titleRow.addView(totalText);
        row.addView(titleRow);

        LinearLayout metrics = new LinearLayout(this);
        LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metricParams.topMargin = dp(8);
        metrics.setLayoutParams(metricParams);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.addView(createMetricCell("Vào", formatTime(summary.firstIn), false));
        metrics.addView(createMetricCell("Ra", formatTime(summary.lastOut), false));
        metrics.addView(createMetricCell("Giờ công", formatStandardWorkDuration(summary), false));
        metrics.addView(createMetricCell(
                "Tăng ca",
                formatOvertimeDuration(summary),
                AttendanceCalculator.overtimeMinutes(summary.workedMinutes) > 0
        ));
        row.addView(metrics);
        return row;
    }

    private View createMetricCell(String label, String value, boolean highlightOvertime) {
        LinearLayout metric = new LinearLayout(this);
        metric.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);

        TextView labelText = new TextView(this);
        labelText.setText(label);
        labelText.setGravity(Gravity.CENTER);
        labelText.setTextColor(resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                android.R.color.darker_gray
        ));
        labelText.setTextSize(10);

        TextView valueText = new TextView(this);
        valueText.setText(value);
        valueText.setGravity(Gravity.CENTER);
        valueText.setMaxLines(1);
        valueText.setTextSize(highlightOvertime ? 14 : 13);
        valueText.setTypeface(valueText.getTypeface(), android.graphics.Typeface.BOLD);
        valueText.setTextColor(highlightOvertime
                ? getColor(R.color.attendance_overtime)
                : resolveThemeColor(
                        com.google.android.material.R.attr.colorOnSurface,
                        android.R.color.black
                ));

        metric.addView(labelText);
        metric.addView(valueText);
        return metric;
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
        return formatCompactDuration(AttendanceCalculator.standardWorkMinutes(summary.workedMinutes));
    }

    private String formatOvertimeDuration(DailySummary summary) {
        if (summary.firstIn != null && summary.lastOut == null) {
            return "Chưa ra";
        }
        return formatCompactDuration(AttendanceCalculator.overtimeMinutes(summary.workedMinutes));
    }

    private String formatCompactDuration(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours <= 0) {
            return minutes + "p";
        }
        if (minutes == 0) {
            return hours + "g";
        }
        return hours + "g " + minutes + "p";
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
