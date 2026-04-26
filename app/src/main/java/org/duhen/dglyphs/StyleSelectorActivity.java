package org.duhen.dglyphs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.List;

public class StyleSelectorActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_TITLE = "title";

    public static final String MODE_RINGTONE = "ringtone";
    public static final String MODE_NOTIFICATION = "notification";
    public static final String MODE_FLIP = "flip";

    private static final String KEY_CALL_STYLE = "call_style";
    private static final String KEY_NOTIF_STYLE = "notif_style";
    private static final String KEY_FLIP_STYLE = "flip_style";

    private String mode;
    private SharedPreferences prefs;
    private ViewPager2 viewPager;
    private StyleSelectorAdapter adapter;
    private List<StyleItem> items;
    private TextView textStyleName;
    private TextView textStyleCounter;
    private BrightnessSliderView seekVibration;
    private Vibrator vibrator;

    public static Intent ringtonesIntent(Context context) {
        Intent i = new Intent(context, StyleSelectorActivity.class);
        i.putExtra(EXTRA_MODE, MODE_RINGTONE);
        i.putExtra(EXTRA_TITLE, context.getString(R.string.card_ringtones));
        return i;
    }

    public static Intent notificationsIntent(Context context) {
        Intent i = new Intent(context, StyleSelectorActivity.class);
        i.putExtra(EXTRA_MODE, MODE_NOTIFICATION);
        i.putExtra(EXTRA_TITLE, context.getString(R.string.card_notifications));
        return i;
    }

    public static Intent flipIntent(Context context) {
        Intent i = new Intent(context, StyleSelectorActivity.class);
        i.putExtra(EXTRA_MODE, MODE_FLIP);
        i.putExtra(EXTRA_TITLE, context.getString(R.string.flip_to_glyph_label));
        return i;
    }

    private static void fixClipping(ViewGroup root) {
        root.setClipChildren(false);
        root.setClipToPadding(false);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) fixClipping((ViewGroup) child);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_style_selector);

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        vibrator = getSystemService(Vibrator.class);

        ((TextView) findViewById(R.id.textSelectorTitle)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        textStyleName = findViewById(R.id.textStyleName);
        textStyleCounter = findViewById(R.id.textStyleCounter);

        items = new ArrayList<>();
        try {
            String[] files = getAssets().list(assetFolder());
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".csv")) {
                        String name = f.substring(0, f.length() - 4);
                        items.add(new StyleItem(assetFolder(), name, name));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        int initialIndex = findIndex(prefs.getString(savedStyleKey(), null));

        viewPager = findViewById(R.id.viewPager);
        viewPager.setUserInputEnabled(true);
        viewPager.setOffscreenPageLimit(1);

        int peekPx = (int) (getResources().getDisplayMetrics().density * 48);
        viewPager.setPadding(peekPx, 0, peekPx, 0);
        viewPager.setClipChildren(false);
        viewPager.setClipToPadding(false);

        adapter = new StyleSelectorAdapter(this, items, vibModeKey());
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(initialIndex, false);
        viewPager.post(() -> fixClipping(viewPager));

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.playAnimationAt(position);
                updateText(position);
            }
        });

        viewPager.post(() -> {
            adapter.playAnimationAt(initialIndex);
            updateText(initialIndex);
        });

        seekVibration = findViewById(R.id.seekVibration);
        seekVibration.setValue(VibratorUtils.readStep(prefs, vibModeKey()));
        seekVibration.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int step = (int) value;
                prefs.edit().putInt(vibModeKey(), step).apply();
                VibratorUtils.vibrate(vibrator,
                        VibratorUtils.durationForStep(step),
                        VibratorUtils.amplitudeForStep(step));
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (viewPager == null || items == null) return;
        int pos = viewPager.getCurrentItem();
        if (pos >= 0 && pos < items.size()) {
            prefs.edit()
                    .putString(savedStyleKey(), items.get(pos).folder() + "/" + items.get(pos).fileName())
                    .putInt(vibModeKey(), (int) seekVibration.getValue())
                    .apply();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) adapter.stopAll();
        GlyphManager.resetFrame();
    }

    private void updateText(int position) {
        if (position >= 0 && position < items.size()) {
            textStyleName.setText(items.get(position).display());
            textStyleCounter.setText((position + 1) + " / " + items.size());
        }
    }

    private String assetFolder() {
        switch (mode) {
            case MODE_NOTIFICATION:
            case MODE_FLIP:
                return GlyphEffects.FOLDER_NOTIF;
            default:
                return GlyphEffects.FOLDER_CALL;
        }
    }

    private String savedStyleKey() {
        switch (mode) {
            case MODE_NOTIFICATION:
                return KEY_NOTIF_STYLE;
            case MODE_FLIP:
                return KEY_FLIP_STYLE;
            default:
                return KEY_CALL_STYLE;
        }
    }

    private String vibModeKey() {
        switch (mode) {
            case MODE_RINGTONE:
                return VibratorUtils.KEY_VIB_CALL;
            case MODE_FLIP:
                return VibratorUtils.KEY_VIB_FLIP;
            default:
                return VibratorUtils.KEY_VIB_NOTIF;
        }
    }

    private int findIndex(String savedValue) {
        if (savedValue == null) return 0;
        for (int i = 0; i < items.size(); i++) {
            if ((items.get(i).folder() + "/" + items.get(i).fileName()).equals(savedValue))
                return i;
        }
        return 0;
    }
}