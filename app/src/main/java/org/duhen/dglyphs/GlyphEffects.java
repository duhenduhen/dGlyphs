package org.duhen.dglyphs;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GlyphEffects {
    private static final int FRAME_DURATION = 20;
    private static volatile Thread currentThread = null;

    private static final GlyphManager.Glyph[] GLYPH_ORDER = {
            GlyphManager.Glyph.CAMERA,
            GlyphManager.Glyph.DIAGONAL,
            GlyphManager.Glyph.MAIN,
            GlyphManager.Glyph.LINE,
            GlyphManager.Glyph.DOT
    };

    public static void play(Context context, String folder, String fileName, Vibrator vibrator) {
        stop();
        Context appCtx = context.getApplicationContext();

        currentThread = new Thread(() -> {
            try (InputStream is = appCtx.getAssets().open(folder + "/" + fileName + ".csv");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                boolean vibratedThisCycle = false;
                String line;
                while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    String[] vals = trimmed.split("[,\\t ]+");
                    if (vals.length < GLYPH_ORDER.length) continue;

                    try {
                        int[] brightness = new int[GLYPH_ORDER.length];
                        boolean anyNonZero = false;
                        for (int i = 0; i < GLYPH_ORDER.length; i++) {
                            brightness[i] = Integer.parseInt(vals[i]);
                            if (brightness[i] > 0) anyNonZero = true;
                        }

                        if (anyNonZero && !vibratedThisCycle) {
                            vibrate(vibrator, 30, 220);
                            vibratedThisCycle = true;
                        }
                        if (!anyNonZero) vibratedThisCycle = false;

                        for (int i = 0; i < GLYPH_ORDER.length; i++) {
                            GlyphManager.setBrightness(GLYPH_ORDER[i], brightness[i]);
                        }
                    } catch (NumberFormatException e) {
                    }

                    try {
                        Thread.sleep(FRAME_DURATION);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
            } finally {
                GlyphManager.toggleAll(false);
            }
        });
        currentThread.start();
    }

    public static void stop() {
        if (currentThread != null && currentThread.isAlive()) {
            currentThread.interrupt();
            try { currentThread.join(100); } catch (InterruptedException ignored) {}
        }
        GlyphManager.toggleAll(false);
    }

    private static void vibrate(Vibrator v, int ms, int amplitude) {
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        }
    }
}