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
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.topjohnwu.superuser.Shell;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Vibrator vibrator;
    private boolean isMasterAllowed;
    private int currentBrightness;

    private MaterialCardView cardNotifications, cardRingtones, cardFlipStyle, cardSleepTime, cardBrightness;
    private TextView textCurrentCallStyle, textCurrentNotifStyle, textCurrentFlipStyle, textSleepTime;
    private MaterialSwitch switchSleepMode, switchAll, switchFlip;
    private Slider slider;

    public static final String PREF_BLINK_STYLE = "glyph_blink_style";

    // if u gonna add extra styles, begin from here
    private final String[] notifStyleValues = {"static", "breath", "blink", "oi", "nope", "why", "bulb_one", "bulb_two", "guiro", "squiggle"};
    private final String[] callStyleValues = {"static", "blink", "pneumatic", "abra", "squirrels", "snaps", "radiate", "tennis", "plot", "scribble"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);
        initViews();

        Shell.getShell(shell -> {
            if (shell.isRoot()) {
                runOnUiThread(() -> {
                    isMasterAllowed = prefs.getBoolean("master_allow", false);
                    currentBrightness = prefs.getInt("brightness", 2048);
                    setupInitialState();
                    setupListeners();
                    checkAllPermissions();
                });
            } else {
                runOnUiThread(this::showRootError);
            }
        });
    }

    private void initViews() {
        cardNotifications = findViewById(R.id.cardNotifications);
        cardRingtones = findViewById(R.id.cardRingtones);
        cardFlipStyle = findViewById(R.id.cardFlipStyle);
        cardSleepTime = findViewById(R.id.cardSleepTime);
        cardBrightness = findViewById(R.id.cardBrightness);
        textCurrentCallStyle = findViewById(R.id.textCurrentCallStyle);
        textCurrentNotifStyle = findViewById(R.id.textCurrentNotifStyle);
        textCurrentFlipStyle = findViewById(R.id.textCurrentFlipStyle);
        slider = findViewById(R.id.sliderMain);
        switchSleepMode = findViewById(R.id.switchSleepMode);
        switchAll = findViewById(R.id.switchAll);
        switchFlip = findViewById(R.id.switchFlip);
        textSleepTime = findViewById(R.id.textSleepTime);
    }

    private void setupInitialState() {
        switchAll.setChecked(isMasterAllowed);
        slider.setValue(mapBrightnessToPosition(currentBrightness));
        slider.setEnabled(isMasterAllowed);
        switchFlip.setEnabled(isMasterAllowed);
        switchSleepMode.setChecked(prefs.getBoolean("sleep_mode_enabled", false));
        updateStyleLabels();
        updateCardStates(isMasterAllowed);
        updateSleepTimeLabel();
    }

    private void setupListeners() {
        cardNotifications.setOnClickListener(v -> showStyleDialog(R.string.card_notifications, "glyph_blink_style_idx", PREF_BLINK_STYLE));
        cardRingtones.setOnClickListener(v -> showStyleDialog(R.string.card_ringtones, "call_style_idx", "call_style_value"));
        cardFlipStyle.setOnClickListener(v -> showStyleDialog(R.string.flip_to_glyph_label, "flip_style_idx", "flip_style_value"));
        cardSleepTime.setOnClickListener(v -> showTimeRangePicker());

        switchSleepMode.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(20, 100);
            prefs.edit().putBoolean("sleep_mode_enabled", isChecked).apply();
        });

        setupLogicSwitches();
    }

    private void setupLogicSwitches() {
        switchFlip.setChecked(prefs.getBoolean("flip_enabled", false));
        if (switchFlip.isChecked() && isMasterAllowed) startService(new Intent(this, FlipToGlyphService.class));

        switchFlip.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(20, 100);
            prefs.edit().putBoolean("flip_enabled", isChecked).apply();
            if (isMasterAllowed) {
                Intent intent = new Intent(this, FlipToGlyphService.class);
                if (isChecked) startService(intent);
                else { stopService(intent); updateHardware(0); }
            }
        });

        switchAll.setOnCheckedChangeListener((v, isChecked) -> {
            quickTick(15, 120);
            isMasterAllowed = isChecked;
            prefs.edit().putBoolean("master_allow", isChecked).apply();
            slider.setEnabled(isChecked);
            switchFlip.setEnabled(isChecked);
            updateCardStates(isChecked);
            if (!isChecked) {
                updateHardware(0);
                stopService(new Intent(this, FlipToGlyphService.class));
            } else if (prefs.getBoolean("flip_enabled", false)) {
                startService(new Intent(this, FlipToGlyphService.class));
            }
            updateTile();
        });

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                quickTick(10, 50);
                currentBrightness = mapPositionToBrightness(value);
                prefs.edit().putInt("brightness", currentBrightness).apply();
                if (isMasterAllowed) previewBrightness(currentBrightness);
            }
        });
    }

    private void showStyleDialog(int titleRes, String idxKey, String valKey) {
        int currentIdx = prefs.getInt(idxKey, 0);
        boolean isCall = valKey.equals("call_style_value");
        int namesArrayRes = isCall ? R.array.call_style_names : R.array.notif_style_names;
        String[] values = isCall ? callStyleValues : notifStyleValues;

        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(namesArrayRes, currentIdx, (dialog, which) -> {
                    String selected = values[which];
                    prefs.edit().putInt(idxKey, which).putString(valKey, selected).apply();
                    updateStyleLabels();
                    if (isMasterAllowed) new Thread(() -> GlyphEffects.run(selected, prefs.getInt("brightness", 2048), vibrator)).start();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateStyleLabels() {
        String[] notifNames = getResources().getStringArray(R.array.notif_style_names);
        String[] callNames = getResources().getStringArray(R.array.call_style_names);

        int nIdx = prefs.getInt("glyph_blink_style_idx", 0);
        int cIdx = prefs.getInt("call_style_idx", 0);
        int fIdx = prefs.getInt("flip_style_idx", 0);

        if (nIdx < notifNames.length) textCurrentNotifStyle.setText(notifNames[nIdx]);
        if (cIdx < callNames.length) textCurrentCallStyle.setText(callNames[cIdx]);
        if (fIdx < notifNames.length) textCurrentFlipStyle.setText(notifNames[fIdx]);
    }

    private void checkAllPermissions() {
        if (!isNotificationServiceEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dialog_permission_notif_title)
                    .setMessage(R.string.dialog_permission_notif_message)
                    .setPositiveButton(R.string.btn_settings, (d, w) -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")))
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
        MaterialCardView[] cards = {cardNotifications, cardRingtones, cardBrightness, cardFlipStyle, cardSleepTime};
        for (MaterialCardView c : cards) { c.setEnabled(enabled); c.setAlpha(alpha); }
        switchSleepMode.setEnabled(enabled);
    }

    private void quickTick(int d, int a) {
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(d, a));
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) GlyphManager.setBrightness(g, val);
    }

    private void updateTile() {
        try { TileService.requestListeningState(this, new ComponentName(this, MasterTileService.class)); } catch (Exception ignored) {}
    }

    private int mapPositionToBrightness(float p) {
        return p == 2 ? 1024 : p == 3 ? 2048 : p == 4 ? 4095 : 512;
    }

    private float mapBrightnessToPosition(int b) {
        return b >= 4095 ? 4f : b >= 2048 ? 3f : b >= 1024 ? 2f : 1f;
    }

    private void previewBrightness(int b) {
        new Thread(() -> { updateHardware(b); try { Thread.sleep(1500); } catch (Exception ignored) {} updateHardware(0); }).start();
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

    private void showTimeRangePicker() {
        int startH = Integer.parseInt(prefs.getString("sleep_start", "23:00").split(":")[0]);
        int startM = Integer.parseInt(prefs.getString("sleep_start", "23:00").split(":")[1]);

        MaterialTimePicker sp = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(startH).setMinute(startM).setTitleText(R.string.sleep_picker_start).build();
        sp.addOnPositiveButtonClickListener(v -> {
            String startTime = String.format("%02d:%02d", sp.getHour(), sp.getMinute());
            MaterialTimePicker ep = new MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(7).setMinute(0).setTitleText(R.string.sleep_picker_end).build();
            ep.addOnPositiveButtonClickListener(v2 -> {
                prefs.edit().putString("sleep_start", startTime).putString("sleep_end", String.format("%02d:%02d", ep.getHour(), ep.getMinute())).apply();
                updateSleepTimeLabel();
            });
            ep.show(getSupportFragmentManager(), "sleep_end");
        });
        sp.show(getSupportFragmentManager(), "sleep_start");
    }
}