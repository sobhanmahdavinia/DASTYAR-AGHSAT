package ir.dastyar.eghsat;

import android.content.Context;
import android.graphics.Color;

public class Theme {
    private static final String PREF = "dastyar_prefs";
    private static final String KEY_MODE = "theme_mode";
    public static final int LIGHT = 0;
    public static final int DARK = 1;
    public static final int BLUE = 2;
    public static final int ORANGE = 3;
    public static final int GREEN = 4;

    public boolean dark, blue, orange, green;
    public int mode;
    public int bg, card, text, muted, primary, primaryText, success, danger, border;

    public static int getMode(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (p.contains(KEY_MODE)) return p.getInt(KEY_MODE, LIGHT);
        return p.getBoolean("dark_mode", false) ? DARK : LIGHT;
    }
    public static void setMode(Context c, int mode) { c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, mode).apply(); }
    public static boolean isDark(Context c) { return getMode(c) == DARK; }
    public static void setDark(Context c, boolean d) { setMode(c, d ? DARK : LIGHT); }

    public static Theme get(Context c) {
        Theme t = new Theme(); t.mode = getMode(c);
        t.dark=t.mode==DARK; t.blue=t.mode==BLUE; t.orange=t.mode==ORANGE; t.green=t.mode==GREEN;
        if (t.dark) {
            t.bg=Color.rgb(10,15,24); t.card=Color.rgb(20,28,40); t.text=Color.rgb(238,245,255); t.muted=Color.rgb(157,174,196);
            t.primary=Color.rgb(72,132,218); t.primaryText=Color.WHITE; t.success=Color.rgb(63,190,137); t.danger=Color.rgb(239,100,112); t.border=Color.rgb(43,58,77);
        } else if (t.blue) {
            t.bg=Color.rgb(7,29,52); t.card=Color.rgb(14,52,86); t.text=Color.rgb(240,248,255); t.muted=Color.rgb(167,201,231);
            t.primary=Color.rgb(33,150,243); t.primaryText=Color.WHITE; t.success=Color.rgb(53,199,145); t.danger=Color.rgb(255,105,120); t.border=Color.rgb(35,91,137);
        } else if (t.orange) {
            t.bg=Color.rgb(252,246,238); t.card=Color.rgb(255,251,245); t.text=Color.rgb(67,42,25); t.muted=Color.rgb(128,99,76);
            t.primary=Color.rgb(198,96,31); t.primaryText=Color.WHITE; t.success=Color.rgb(69,126,74); t.danger=Color.rgb(190,55,45); t.border=Color.rgb(235,211,188);
        } else if (t.green) {
            t.bg=Color.rgb(242,249,245); t.card=Color.rgb(252,255,253); t.text=Color.rgb(24,55,39); t.muted=Color.rgb(91,121,104);
            t.primary=Color.rgb(46,125,82); t.primaryText=Color.WHITE; t.success=Color.rgb(30,133,80); t.danger=Color.rgb(194,65,65); t.border=Color.rgb(202,225,211);
        } else {
            t.bg=Color.rgb(247,249,252); t.card=Color.WHITE; t.text=Color.rgb(24,35,50); t.muted=Color.rgb(103,119,139);
            t.primary=Color.rgb(45,91,153); t.primaryText=Color.WHITE; t.success=Color.rgb(22,138,88); t.danger=Color.rgb(198,40,40); t.border=Color.rgb(220,228,238);
        }
        return t;
    }
}
