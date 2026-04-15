package org.duhen.dglyphs;

import com.topjohnwu.superuser.Shell;

// thanks to myglyph for initial glyph manager impl

public class GlyphManager {
    public static final int MAX_BRIGHTNESS = 4095;
    private static final String PATH_ROOT = "/sys/devices/platform/soc/984000.i2c/i2c-0/0-0020/leds/aw210xx_led";

    public static void setBrightness(Glyph glyph, int brightness) {
        if (!Shell.getShell().isRoot()) return;
        int safeBrightness = Math.max(0, Math.min(brightness, MAX_BRIGHTNESS));
        Shell.cmd("echo " + safeBrightness + " > " + glyph.path).submit();
    }

    public static void setFrame(int[] values) {
        if (values == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(Math.max(0, Math.min(values[i], MAX_BRIGHTNESS)));
            if (i < values.length - 1) sb.append(" ");
        }
        Shell.cmd("echo " + sb + " > " + PATH_ROOT + "/frame_leds_effect").submit();
    }

    public static void resetFrame() {
        Shell.cmd("echo 0 > " + PATH_ROOT + "/all_leds_effect").submit();
    }

    public enum Glyph {
        CAMERA(PATH_ROOT + "/rear_cam_led_br"),
        DIAGONAL(PATH_ROOT + "/front_cam_led_br"),
        MAIN(PATH_ROOT + "/round_leds_br"),
        LINE(PATH_ROOT + "/vline_leds_br"),
        DOT(PATH_ROOT + "/dot_led_br");

        public final String path;

        Glyph(String path) {
            this.path = path;
        }
    }
}