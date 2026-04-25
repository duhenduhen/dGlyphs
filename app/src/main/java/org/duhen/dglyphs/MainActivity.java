package org.duhen.dglyphs;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.topjohnwu.superuser.Shell;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_CALL_STYLE = "call_style";
    private static final String KEY_NOTIF_STYLE = "notif_style";
    private static final String KEY_FLIP_STYLE = "flip_style";
    private static final String KEY_MASTER_ALLOW = "master_allow";
    private static final String KEY_BRIGHTNESS = "brightness";

    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final androidx.activity.result.ActivityResultLauncher<Intent> flipStyleLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                    });
    private SharedPreferences prefs;
    private final androidx.activity.result.ActivityResultLauncher<Intent> notifStyleLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) refreshNotifStyleLabel();
                    });
    private Vibrator vibrator;
    private boolean isMasterAllowed;
    private int currentBrightness;
    private MaterialCardView cardNotifications, cardRingtones, cardFlipStyle, cardTurnOff, cardSleepTime, cardBrightness, cardBattery, cardVolume, cardExtra;
    private TextView textCurrentCallStyle, textCurrentNotifStyle, textSleepTime;
    private final androidx.activity.result.ActivityResultLauncher<Intent> callStyleLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) refreshCallStyleLabel();
                    });
    private MaterialSwitch switchSleepMode, switchAll, switchFlip, switchBattery, switchLockscreenOnly, switchVolume;
    private BrightnessSliderView slider;
    private final SharedPreferences.OnSharedPreferenceChangeListener rootPrefListener = (sharedPrefs, key) -> {
        if (KEY_MASTER_ALLOW.equals(key)) {
            boolean newValue = sharedPrefs.getBoolean(KEY_MASTER_ALLOW, false);
            runOnUiThread(() -> {
                if (switchAll != null && switchAll.isChecked() != newValue) {
                    isMasterAllowed = newValue;
                    switchAll.setChecked(newValue);
                    updateCardStates(newValue);
                }
            });
        }
    };
    private ImageView spacewar;
    private Runnable previewRunnable;

    private static String lastSegment(String s) {
        if (s == null) return "";
        int idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

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

        initViews();

        Shell.getShell(shell -> {
            if (shell.isRoot()) {
                runOnUiThread(() -> {
                    isMasterAllowed = prefs.getBoolean(KEY_MASTER_ALLOW, false);
                    currentBrightness = prefs.getInt(KEY_BRIGHTNESS, 2048);
                    setupInitialState();
                    setupListeners();
                    checkAllPermissions();
                    setupCollapse();
                });
            } else {
                runOnUiThread(this::showRootError);
            }
        });
    }

    private void refreshCallStyleLabel() {
        String val = prefs.getString(KEY_CALL_STYLE, null);
        textCurrentCallStyle.setText(val != null ? lastSegment(val) : getString(R.string.style_unknown));
    }

    private void refreshNotifStyleLabel() {
        String val = prefs.getString(KEY_NOTIF_STYLE, null);
        textCurrentNotifStyle.setText(val != null ? lastSegment(val) : getString(R.string.style_unknown));
    }

    private void initViews() {
        cardNotifications = findViewById(R.id.cardNotifications);
        cardRingtones = findViewById(R.id.cardRingtones);
        cardFlipStyle = findViewById(R.id.cardFlipStyle);
        cardTurnOff = findViewById(R.id.cardTurnOff);
        cardSleepTime = findViewById(R.id.cardSleepTime);
        cardBrightness = findViewById(R.id.cardBrightness);
        cardBattery = findViewById(R.id.cardBattery);
        cardVolume = findViewById(R.id.cardVolume);
        cardExtra = findViewById(R.id.cardExtra);

        textCurrentCallStyle = findViewById(R.id.textCurrentCallStyle);
        textCurrentNotifStyle = findViewById(R.id.textCurrentNotifStyle);
        textSleepTime = findViewById(R.id.textSleepTime);

        slider = findViewById(R.id.sliderMain);
        switchSleepMode = findViewById(R.id.switchSleepMode);
        switchAll = findViewById(R.id.switchAll);
        switchFlip = findViewById(R.id.switchFlip);
        switchLockscreenOnly = findViewById(R.id.switchLockscreenOnly);
        switchBattery = findViewById(R.id.switchBattery);
        switchVolume = findViewById(R.id.switchVolume);
        spacewar = findViewById(R.id.spacewar);
    }

    private void setupInitialState() {
        switchAll.setChecked(isMasterAllowed);
        switchLockscreenOnly.setChecked(prefs.getBoolean("lockscreen_only", false));
        switchSleepMode.setChecked(prefs.getBoolean("sleep_mode_enabled", false));
        switchBattery.setChecked(prefs.getBoolean("battery_glyph_enabled", false));
        switchVolume.setChecked(prefs.getBoolean("volume_glyph_enabled", false));

        applyDefaultStylesIfNeeded();
        refreshCallStyleLabel();
        refreshNotifStyleLabel();
        updateSleepTimeLabel();
        slider.setValue(mapBrightnessToPosition(currentBrightness));
        updateOutlineAlpha(slider.getValue());
        updateCardStates(isMasterAllowed);
    }

    private void applyDefaultStylesIfNeeded() {
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_NOTIF_STYLE)) {
            String first = firstCsvName("notification");
            if (first != null) {
                editor.putString(KEY_NOTIF_STYLE, "notification/" + first);
                changed = true;
            }
        }
        if (!prefs.contains(KEY_CALL_STYLE)) {
            String first = firstCsvName("call");
            if (first != null) {
                editor.putString(KEY_CALL_STYLE, "call/" + first);
                changed = true;
            }
        }
        if (!prefs.contains(KEY_FLIP_STYLE)) {
            String first = firstCsvName("notification");
            if (first != null) {
                editor.putString(KEY_FLIP_STYLE, "notification/" + first);
                changed = true;
            }
        }
        if (changed) editor.apply();
    }

    private String firstCsvName(String folder) {
        try {
            String[] files = getAssets().list(folder);
            if (files == null) return null;
            for (String f : files) {
                if (f.endsWith(".csv")) return f.replace(".csv", "");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void setupListeners() {
        cardRingtones.setOnClickListener(v ->
                callStyleLauncher.launch(StyleSelectorActivity.ringtonesIntent(this)));
        cardNotifications.setOnClickListener(v ->
                notifStyleLauncher.launch(StyleSelectorActivity.notificationsIntent(this)));
        cardFlipStyle.setOnClickListener(v ->
                flipStyleLauncher.launch(StyleSelectorActivity.flipIntent(this)));
        cardSleepTime.setOnClickListener(v -> startActivity(new Intent(this, SleepModeActivity.class)));
        cardExtra.setOnClickListener(v -> startActivity(new Intent(this, ExtraActivity.class)));

        switchSleepMode.setOnCheckedChangeListener((v, isChecked) -> {
            VibratorUtils.quickTick(vibrator, 15, 100);
            prefs.edit().putBoolean("sleep_mode_enabled", isChecked).apply();
        });
        switchBattery.setOnCheckedChangeListener((v, isChecked) -> {
            VibratorUtils.quickTick(vibrator, 15, 100);
            prefs.edit().putBoolean("battery_glyph_enabled", isChecked).apply();
            if (isChecked && isMasterAllowed)
                ServiceManager.startIfEnabled(this, prefs, "battery_glyph_enabled", BatteryGlyphService.class);
            else ServiceManager.stop(this, BatteryGlyphService.class);
        });
        switchVolume.setOnCheckedChangeListener((v, isChecked) -> {
            VibratorUtils.quickTick(vibrator, 15, 100);
            prefs.edit().putBoolean("volume_glyph_enabled", isChecked).apply();
            if (isChecked && isMasterAllowed)
                ServiceManager.startIfEnabled(this, prefs, "volume_glyph_enabled", VolumeGlyphService.class);
            else ServiceManager.stop(this, VolumeGlyphService.class);
        });
        switchLockscreenOnly.setOnCheckedChangeListener((v, isChecked) -> {
            VibratorUtils.quickTick(vibrator, 15, 100);
            prefs.edit().putBoolean("lockscreen_only", isChecked).apply();
        });

        setupLogicSwitches();
    }

    private void setupLogicSwitches() {
        switchFlip.setChecked(prefs.getBoolean("flip_enabled", false));
        switchFlip.setOnCheckedChangeListener((v, isChecked) -> {
            VibratorUtils.quickTick(vibrator, 15, 100);
            prefs.edit().putBoolean("flip_enabled", isChecked).apply();
            if (isMasterAllowed) {
                if (isChecked)
                    ServiceManager.startIfEnabled(this, prefs, "flip_enabled", FlipToGlyphService.class);
                else {
                    ServiceManager.stop(this, FlipToGlyphService.class);
                    updateHardware(0);
                }
            }
        });

        switchAll.setOnCheckedChangeListener((v, isChecked) -> {
            VibratorUtils.quickTick(vibrator, 15, 100);
            isMasterAllowed = isChecked;
            prefs.edit().putBoolean(KEY_MASTER_ALLOW, isChecked).apply();
            updateCardStates(isChecked);
            if (!isChecked) {
                GlyphEffects.stop();
                updateHardware(0);
                ServiceManager.stopAll(this);
            } else {
                ServiceManager.startAll(this, prefs);
            }
            updateTile();
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            updateOutlineAlpha(value);
            if (fromUser) {
                int brightness = mapPositionToBrightness(value);
                if (brightness != currentBrightness) {
                    VibratorUtils.quickTick(vibrator, 10, 50);
                    currentBrightness = brightness;
                    prefs.edit().putInt(KEY_BRIGHTNESS, currentBrightness).apply();
                    if (isMasterAllowed) {
                        if (previewRunnable != null)
                            previewHandler.removeCallbacks(previewRunnable);
                        updateHardware(currentBrightness);
                        previewRunnable = () -> updateHardware(0);
                        previewHandler.postDelayed(previewRunnable, 1500);
                    }
                }
            }
        });
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
        MaterialCardView[] cards = {cardNotifications, cardRingtones, cardBrightness, cardFlipStyle, cardSleepTime, cardTurnOff, cardBattery, cardVolume, cardExtra};
        for (MaterialCardView c : cards) {
            c.setEnabled(enabled);
            c.setAlpha(alpha);
        }
        slider.setEnabled(enabled);
        switchFlip.setEnabled(enabled);
        switchSleepMode.setEnabled(enabled);
        switchBattery.setEnabled(enabled);
        switchVolume.setEnabled(enabled);
        switchLockscreenOnly.setEnabled(enabled);
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) GlyphManager.setBrightness(g, val);
    }

    private void updateTile() {
        try {
            TileService.requestListeningState(this, new ComponentName(this, MasterTileService.class));
            TileService.requestListeningState(this, new ComponentName(this, GlyphTileService.class));
            TileService.requestListeningState(this, new ComponentName(this, RandomGlyphTileService.class));
        } catch (Exception ignored) {
        }
    }

    private int mapPositionToBrightness(float p) {
        return p == 2 ? 1024 : p == 3 ? 2048 : p == 4 ? 4095 : 512;
    }

    private float mapBrightnessToPosition(int b) {
        return b >= 4095 ? 4f : b >= 2048 ? 3f : b >= 1024 ? 2f : 1f;
    }

    private void setupCollapse() {
        AppBarLayout appBar = findViewById(R.id.appBarLayout);
        CollapsingToolbarLayout ctl = findViewById(R.id.collapsingToolbar);
        if (appBar == null || ctl == null) return;

        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true);
        int baseColor = tv.data & 0x00FFFFFF;

        appBar.addOnOffsetChangedListener((bar, verticalOffset) -> {
            int total = bar.getTotalScrollRange();
            if (total == 0) return;
            int alpha = (int) (255 * (1f - (float) Math.abs(verticalOffset) / total));
            ctl.setExpandedTitleColor((alpha << 24) | baseColor);
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
        if (prefs != null) {
            isMasterAllowed = prefs.getBoolean(KEY_MASTER_ALLOW, false);
            setupInitialState();
            prefs.registerOnSharedPreferenceChangeListener(rootPrefListener);
        }
    }
}