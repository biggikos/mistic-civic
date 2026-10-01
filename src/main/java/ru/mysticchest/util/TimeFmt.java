package ru.mysticchest.util;

public final class TimeFmt {
    private TimeFmt() {}

    /** 3725 -> "1h 2m 5s" (units are supplied by the language file). */
    public static String format(long seconds, String d, String h, String m, String s) {
        if (seconds < 1) seconds = 1;
        long days = seconds / 86400, hours = seconds % 86400 / 3600, mins = seconds % 3600 / 60, secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(d).append(' ');
        if (hours > 0) sb.append(hours).append(h).append(' ');
        if (mins > 0) sb.append(mins).append(m).append(' ');
        if (secs > 0 || sb.length() == 0) sb.append(secs).append(s).append(' ');
        return sb.substring(0, sb.length() - 1);
    }
}
