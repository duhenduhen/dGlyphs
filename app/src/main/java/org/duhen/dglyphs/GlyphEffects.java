package org.duhen.dglyphs;

import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

// todo: more smooth?

public class GlyphEffects {

    public static void run(String style, int brightness, Vibrator vibrator) {
        switch (style) {
            // styles from 1.0
            case "static":
                vibrate(vibrator, 30);
                updateAll(brightness);
                SystemClock.sleep(400);
                updateAll(0);
                break;

            case "breath":
                for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) {
                    flash(g, brightness, 100, vibrator);
                }
                break;

            case "blink":
                for (int i = 0; i < 2; i++) {
                    vibrate(vibrator, 25);
                    updateAll(brightness);
                    SystemClock.sleep(100);
                    updateAll(0);
                    SystemClock.sleep(100);
                }
                break;

            // calls
            // ported from nos
            // some of them suck
            case "pneumatic":
                for (int i = 0; i < 10; i++) flash(GlyphManager.Glyph.LINE, brightness, 40, vibrator);
                break;

            case "abra":
                for (int cycle = 0; cycle < 2; cycle++) {
                    flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                    flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                    flash(GlyphManager.Glyph.CAMERA, brightness, 100, vibrator);
                    flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                    flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                }
                for (int i = 0; i < 4; i++) flash(GlyphManager.Glyph.LINE, brightness, 80, vibrator);
                break;

            case "squirrels":
                for (int i = 0; i < 3; i++) flash(GlyphManager.Glyph.CAMERA, brightness, 50, vibrator);
                flash(GlyphManager.Glyph.MAIN, brightness, 100, vibrator);
                flash(GlyphManager.Glyph.LINE, brightness, 100, vibrator);
                break;

            case "snaps":
                for (int i = 0; i < 5; i++) flash(GlyphManager.Glyph.DOT, brightness, 150, vibrator);
                break;

            case "radiate":
                flash(GlyphManager.Glyph.LINE, brightness, 60, vibrator);
                for (int i = 0; i < 7; i++) flash(GlyphManager.Glyph.MAIN, brightness, 50, vibrator);
                break;

            case "tennis":
                for (int i = 0; i < 3; i++) flash(GlyphManager.Glyph.LINE, brightness, 60, vibrator);
                flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                flash(GlyphManager.Glyph.LINE, brightness, 80, vibrator);
                flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                break;

            case "plot":
                flash(GlyphManager.Glyph.LINE, brightness, 150, vibrator);
                flash(GlyphManager.Glyph.CAMERA, brightness, 150, vibrator);

                for (int i = 0; i < 2; i++) flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);

                flash(GlyphManager.Glyph.LINE, brightness, 150, vibrator);
                for (int i = 0; i < 5; i++) flash(GlyphManager.Glyph.DOT, brightness, 60, vibrator);

                flash(GlyphManager.Glyph.LINE, brightness, 300, vibrator);
                flash(GlyphManager.Glyph.CAMERA, brightness, 300, vibrator);

                for (int i = 0; i < 9; i++) {
                    vibrate(vibrator, 10);
                    GlyphManager.setBrightness(GlyphManager.Glyph.MAIN, brightness);
                    GlyphManager.setBrightness(GlyphManager.Glyph.LINE, brightness);
                    SystemClock.sleep(60);
                    GlyphManager.setBrightness(GlyphManager.Glyph.MAIN, 0);
                    GlyphManager.setBrightness(GlyphManager.Glyph.LINE, 0);
                    SystemClock.sleep(60);
                }
                break;

            case "scribble":
                flash(GlyphManager.Glyph.CAMERA, brightness, 70, vibrator);
                flash(GlyphManager.Glyph.LINE, brightness, 70, vibrator);
                flash(GlyphManager.Glyph.CAMERA, brightness, 70, vibrator);
                flash(GlyphManager.Glyph.LINE, brightness, 70, vibrator);
                flash(GlyphManager.Glyph.DOT, brightness, 70, vibrator);

                for (int i = 0; i < 3; i++) flash(GlyphManager.Glyph.LINE, brightness, 70, vibrator);

                for (int i = 0; i < 2; i++) {
                    vibrate(vibrator, 20);
                    GlyphManager.setBrightness(GlyphManager.Glyph.LINE, brightness);
                    GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, brightness);
                    SystemClock.sleep(70);
                    GlyphManager.setBrightness(GlyphManager.Glyph.LINE, 0);
                    GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, 0);
                    SystemClock.sleep(70);
                }

                for (int i = 0; i < 2; i++) flash(GlyphManager.Glyph.LINE, brightness, 120, vibrator);
                break;

            // notifications
            // from nos
            // some of them suck
            case "oi":
                for (int i = 0; i < 3; i++) flash(GlyphManager.Glyph.DOT, brightness, 80, vibrator);
                break;

            case "nope":
                for (int i = 0; i < 3; i++) {
                    vibrate(vibrator, 20);
                    GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, brightness);
                    GlyphManager.setBrightness(GlyphManager.Glyph.MAIN, brightness);
                    SystemClock.sleep(100);
                    GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, 0);
                    GlyphManager.setBrightness(GlyphManager.Glyph.MAIN, 0);
                    SystemClock.sleep(100);
                }
                break;

            case "why":
                flash(GlyphManager.Glyph.DIAGONAL, brightness, 100, vibrator);
                flash(GlyphManager.Glyph.CAMERA, brightness, 100, vibrator);
                flash(GlyphManager.Glyph.MAIN, brightness, 100, vibrator);
                break;

            case "bulb_one":
                flash(GlyphManager.Glyph.MAIN, brightness, 120, vibrator);
                flash(GlyphManager.Glyph.LINE, brightness, 120, vibrator);
                flash(GlyphManager.Glyph.DOT, brightness, 300, vibrator);
                break;

            case "bulb_two":
                vibrate(vibrator, 50);
                GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, brightness);
                GlyphManager.setBrightness(GlyphManager.Glyph.MAIN, brightness);
                GlyphManager.setBrightness(GlyphManager.Glyph.LINE, brightness);
                SystemClock.sleep(300);
                updateAll(0);
                SystemClock.sleep(100);
                flash(GlyphManager.Glyph.DIAGONAL, brightness, 300, vibrator);
                break;

            case "guiro":
                GlyphManager.Glyph[] order = {GlyphManager.Glyph.CAMERA, GlyphManager.Glyph.MAIN, GlyphManager.Glyph.LINE, GlyphManager.Glyph.DOT};
                for (GlyphManager.Glyph g : order) flash(g, brightness, 60, vibrator);
                break;

            case "squiggle":
                for (int i = 0; i < 2; i++) {
                    vibrate(vibrator, 15);
                    GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, brightness);
                    GlyphManager.setBrightness(GlyphManager.Glyph.LINE, brightness);
                    SystemClock.sleep(70);
                    GlyphManager.setBrightness(GlyphManager.Glyph.CAMERA, 0);
                    GlyphManager.setBrightness(GlyphManager.Glyph.LINE, 0);
                    SystemClock.sleep(70);
                }

                flash(GlyphManager.Glyph.LINE, brightness, 70, vibrator);

                for (int i = 0; i < 2; i++) {
                    flash(GlyphManager.Glyph.DOT, brightness, 70, vibrator);
                }
                break;
        }
    }

    private static void flash(GlyphManager.Glyph g, int b, int d, Vibrator v) {
        vibrate(v, 15);
        GlyphManager.setBrightness(g, b);
        SystemClock.sleep(d);
        GlyphManager.setBrightness(g, 0);
        SystemClock.sleep(d);
    }

    private static void updateAll(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) {
            GlyphManager.setBrightness(g, val);
        }
    }

    private static void vibrate(Vibrator v, int ms) {
        if (v != null && v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}