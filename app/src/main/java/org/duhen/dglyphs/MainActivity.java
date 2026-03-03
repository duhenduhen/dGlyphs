package org.duhen.dglyphs;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.topjohnwu.superuser.Shell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String PREF_BLINK_STYLE = "glyph_blink_style";
    private final android.os.Handler previewHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private String[] notifStyleValues;
    private String[] callStyleValues;

    private SharedPreferences prefs;
    private Vibrator vibrator;
    private boolean isMasterAllowed;
    private int currentBrightness;

    private MaterialCardView cardNotifications, cardRingtones, cardFlipStyle, cardTurnOff, cardSleepTime, cardBrightness, cardBattery;
    private TextView textCurrentCallStyle, textCurrentNotifStyle, textCurrentFlipStyle, textSleepTime;
    private MaterialSwitch switchSleepMode, switchAll, switchFlip, switchBattery, switchLockscreenOnly;
    private Slider slider;
    private Runnable previewRunnable;
    private ImageView spacewar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);
        notifStyleValues = loadStyleNames("notification");
        callStyleValues = loadStyleNames("call");

        initViews();

        Shell.getShell(shell -> {
            if (shell.isRoot()) {
                runOnUiThread(() -> {
                    isMasterAllowed = prefs.getBoolean("master_allow", false);
                    currentBrightness = prefs.getInt("brightness", 2048);
                    setupInitialState();
                    setupListeners();
                    checkAllPermissions();
                    setupSmoothCollapse();
                });
            } else {
                runOnUiThread(this::showRootError);
            }
        });
    }

    private String[] loadStyleNames(String folder) {
        try {
            String[] files = getAssets().list(folder);
            if (files == null || files.length == 0) return new String[]{};
            List<String> names = new ArrayList<>();
            for (String f : files) {
                if (f.endsWith(".csv")) {
                    names.add(f.replace(".csv", ""));
                }
            }
            Collections.sort(names);
            return names.toArray(new String[0]);
        } catch (Exception e) {
            return new String[]{};
        }
    }

    private void initViews() {
        cardNotifications = findViewById(R.id.cardNotifications);
        cardRingtones = findViewById(R.id.cardRingtones);
        cardFlipStyle = findViewById(R.id.cardFlipStyle);
        cardTurnOff = findViewById(R.id.cardTurnOff);
        cardSleepTime = findViewById(R.id.cardSleepTime);
        cardBrightness = findViewById(R.id.cardBrightness);
        cardBattery = findViewById(R.id.cardBattery);

        textCurrentCallStyle = findViewById(R.id.textCurrentCallStyle);
        textCurrentNotifStyle = findViewById(R.id.textCurrentNotifStyle);
        textCurrentFlipStyle = findViewById(R.id.textCurrentFlipStyle);
        textSleepTime = findViewById(R.id.textSleepTime);

        slider = findViewById(R.id.sliderMain);
        switchSleepMode = findViewById(R.id.switchSleepMode);
        switchAll = findViewById(R.id.switchAll);
        switchFlip = findViewById(R.id.switchFlip);
        switchLockscreenOnly = findViewById(R.id.switchLockscreenOnly);
        switchBattery = findViewById(R.id.switchBattery);
        spacewar = findViewById(R.id.spacewar);
    }

    private void setupInitialState() {
        switchAll.setChecked(isMasterAllowed);
        switchLockscreenOnly.setChecked(prefs.getBoolean("lockscreen_only", false));
        switchSleepMode.setChecked(prefs.getBoolean("sleep_mode_enabled", false));
        switchBattery.setChecked(prefs.getBoolean("battery_glyph_enabled", false));
        updateStyleLabels();
        updateSleepTimeLabel();
        slider.setValue(mapBrightnessToPosition(currentBrightness));
        updateOutlineAlpha(slider.getValue());
        updateCardStates(isMasterAllowed);
    }

    private void setupListeners() {

        cardNotifications.setOnClickListener(v -> showStyleDialog(
                R.string.card_notifications, "glyph_blink_style_idx", PREF_BLINK_STYLE, "notification", notifStyleValues));
        cardRingtones.setOnClickListener(v -> showStyleDialog(
                R.string.card_ringtones, "call_style_idx", "call_style_value", "call", callStyleValues));
        cardFlipStyle.setOnClickListener(v -> showStyleDialog(
                R.string.flip_to_glyph_label, "flip_style_idx", "flip_style_value", "notification", notifStyleValues));

        cardSleepTime.setOnClickListener(v -> startActivity(new Intent(this, SleepModeActivity.class)));

        switchSleepMode.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(20, 100);
            prefs.edit().putBoolean("sleep_mode_enabled", isChecked).apply();
        });

        switchBattery.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("battery_glyph_enabled", isChecked).apply();
            Intent intent = new Intent(this, BatteryGlyphService.class);
            if (isChecked && isMasterAllowed) startService(intent);
            else stopService(intent);
        });

        switchLockscreenOnly.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(15, 100);
            prefs.edit().putBoolean("lockscreen_only", isChecked).apply();
        });

        setupLogicSwitches();

    }

    private void setupLogicSwitches() {
        switchFlip.setChecked(prefs.getBoolean("flip_enabled", false));

        switchFlip.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(20, 100);
            prefs.edit().putBoolean("flip_enabled", isChecked).apply();
            if (isMasterAllowed) {
                Intent intent = new Intent(this, FlipToGlyphService.class);
                if (isChecked) startService(intent);
                else {
                    stopService(intent);
                    updateHardware(0);
                }
            }
        });

        switchAll.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(15, 120);
            isMasterAllowed = isChecked;
            prefs.edit().putBoolean("master_allow", isChecked).apply();
            updateCardStates(isChecked);
            if (!isChecked) {
                GlyphEffects.stop();
                updateHardware(0);
                stopService(new Intent(this, FlipToGlyphService.class));
                stopService(new Intent(this, BatteryGlyphService.class));
            } else {
                if (prefs.getBoolean("flip_enabled", false))
                    startService(new Intent(this, FlipToGlyphService.class));
                if (prefs.getBoolean("battery_glyph_enabled", false))
                    startService(new Intent(this, BatteryGlyphService.class));
            }
            updateTile();
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            updateOutlineAlpha(value);
            if (fromUser) {
                int brightness = mapPositionToBrightness(value);
                if (brightness != currentBrightness) {
                    quickTick(10, 50);
                    currentBrightness = brightness;
                    prefs.edit().putInt("brightness", currentBrightness).apply();
                    if (isMasterAllowed) {
                        if (previewRunnable != null) previewHandler.removeCallbacks(previewRunnable);
                        updateHardware(currentBrightness);
                        previewRunnable = () -> updateHardware(0);
                        previewHandler.postDelayed(previewRunnable, 1500);
                    }
                }
            }
        });
    }

    private void showStyleDialog(int titleRes, String idxKey, String valKey, String folder, String[] values) {
        if (values.length == 0) return;

        int currentIdx = Math.min(prefs.getInt(idxKey, 0), values.length - 1);
        final int[] selectedIdx = {currentIdx};

        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(values, currentIdx, (dialog, which) -> {
                    selectedIdx[0] = which;
                    if (isMasterAllowed) {
                        GlyphEffects.play(this, folder, values[which], vibrator);
                    }
                })
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    int finalIdx = selectedIdx[0];
                    prefs.edit()
                            .putInt(idxKey, finalIdx)
                            .putString(valKey, values[finalIdx])
                            .apply();
                    updateStyleLabels();
                    GlyphEffects.stop();
                    quickTick(15, 100);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> GlyphEffects.stop())
                .show();
    }

    private void updateStyleLabels() {
        int nIdx = prefs.getInt("glyph_blink_style_idx", 0);
        int cIdx = prefs.getInt("call_style_idx", 0);
        int fIdx = prefs.getInt("flip_style_idx", 0);

        if (notifStyleValues.length > 0)
            textCurrentNotifStyle.setText(notifStyleValues[Math.min(nIdx, notifStyleValues.length - 1)]);
        if (callStyleValues.length > 0)
            textCurrentCallStyle.setText(callStyleValues[Math.min(cIdx, callStyleValues.length - 1)]);
        if (notifStyleValues.length > 0)
            textCurrentFlipStyle.setText(notifStyleValues[Math.min(fIdx, notifStyleValues.length - 1)]);
    }

    private void checkAllPermissions() {
        if (!isNotificationServiceEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_permission_notif_title)
                    .setMessage(R.string.dialog_permission_notif_message)
                    .setPositiveButton(R.string.btn_settings, (d, w) ->
                            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")))
                    .show();
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 101);
        }
    }

    private boolean isNotificationServiceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void updateCardStates(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.5f;
        MaterialCardView[] cards = {cardNotifications, cardRingtones, cardBrightness, cardFlipStyle, cardSleepTime, cardTurnOff, cardBattery};
        for (MaterialCardView c : cards) {
            c.setEnabled(enabled);
            c.setAlpha(alpha);
        }
        slider.setEnabled(enabled);
        switchFlip.setEnabled(enabled);
        switchSleepMode.setEnabled(enabled);
        switchBattery.setEnabled(enabled);
        switchLockscreenOnly.setEnabled(enabled);
    }

    private void quickTick(int d, int a) {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(d, a));
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) GlyphManager.setBrightness(g, val);
    }

    private void updateTile() {
        try {
            TileService.requestListeningState(this, new ComponentName(this, MasterTileService.class));
        } catch (Exception ignored) {}
    }

    private int mapPositionToBrightness(float p) {
        return p == 2 ? 1024 : p == 3 ? 2048 : p == 4 ? 4095 : 512;
    }

    private float mapBrightnessToPosition(int b) {
        return b >= 4095 ? 4f : b >= 2048 ? 3f : b >= 1024 ? 2f : 1f;
    }

    private void setupSmoothCollapse() {
        AppBarLayout appBar = findViewById(R.id.appBarLayout);
        CollapsingToolbarLayout ctl = findViewById(R.id.collapsingToolbar);
        if (appBar == null || ctl == null) return;

        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        int colorOnSurface = typedValue.data;

        appBar.addOnOffsetChangedListener((bar, verticalOffset) -> {
            int totalScrollRange = bar.getTotalScrollRange();
            if (totalScrollRange == 0) return;
            float fraction = Math.abs((float) verticalOffset / totalScrollRange);
            float smooth = fraction * fraction * (3f - 2f * fraction);
            int alpha = (int) (255 * (1f - smooth));
            int color = (alpha << 24) | (colorOnSurface & 0x00FFFFFF);
            ctl.setExpandedTitleColor(color);
        });
    }

    private void showRootError() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.root_error_title).setMessage(R.string.root_error_message)
                .setCancelable(false).setPositiveButton(R.string.exit, (d, w) -> finish()).show();
        switchAll.setEnabled(false);
    }

    private void updateSleepTimeLabel() {
        textSleepTime.setText(prefs.getString("sleep_start", "23:00") + " - " + prefs.getString("sleep_end", "07:00"));
    }

    private void updateOutlineAlpha(float sliderValue) {
        if (spacewar != null) {
            float alpha = 0.2f + ((sliderValue - 1) / 3f) * 0.8f;
            spacewar.setAlpha(alpha);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isMasterAllowed) setupInitialState();
    }
}