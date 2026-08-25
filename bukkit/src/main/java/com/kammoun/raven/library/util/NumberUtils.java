package com.kammoun.raven.library.util;

import java.text.DecimalFormat;

public final class NumberUtils {

    private static final DecimalFormat COMMA_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");

    private NumberUtils() {}

    public static int parseInt(String value) {
        if (value == null) return 0;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    public static long parseLong(String value) {
        if (value == null) return 0;
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    public static double parseDouble(String value) {
        if (value == null) return 0;
        try { return Double.parseDouble(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    public static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) return false;
        try { Double.parseDouble(value.trim()); return true; }
        catch (NumberFormatException e) { return false; }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double percent(double part, double total) {
        if (total == 0) return 0;
        return (part / total) * 100.0;
    }

    public static double round(double value, int places) {
        if (places < 0) return value;
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    public static String format(long value) {
        return COMMA_FORMAT.format(value);
    }

    public static String format(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    public static String compact(long value) {
        return compact((double) value);
    }

    public static String compact(double value) {
        return compact(value, "");
    }

    public static String compactMoney(double value) {
        return compact(value, "$");
    }

    private static String compact(double value, String smallSuffix) {
        double absolute = Math.abs(value);
        String sign = value < 0 ? "-" : "";

        if (absolute < 1_000D) return sign + trimOneDecimal(absolute) + smallSuffix;
        if (absolute < 1_000_000D) return sign + trimOneDecimal(absolute / 1_000D) + "k";
        if (absolute < 1_000_000_000D) return sign + trimOneDecimal(absolute / 1_000_000D) + "m";
        if (absolute < 1_000_000_000_000D) return sign + trimOneDecimal(absolute / 1_000_000_000D) + "b";
        return sign + trimOneDecimal(absolute / 1_000_000_000_000D) + "t";
    }

    private static String trimOneDecimal(double value) {
        double rounded = Math.round(value * 10D) / 10D;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(java.util.Locale.US, "%.1f", rounded);
    }

    public static String formatDouble(double value, int places) {
        if (places < 0) places = 0;
        DecimalFormat df = new DecimalFormat("#,##0" + (places > 0 ? "." + "0".repeat(places) : ""));
        return df.format(value);
    }

    public static String abbreviate(long value) {
        if (value >= 1_000_000_000L) return round(value / 1_000_000_000.0, 1) + "B";
        if (value >= 1_000_000L)     return round(value / 1_000_000.0, 1) + "M";
        if (value >= 1_000L)         return round(value / 1_000.0, 1) + "K";
        return String.valueOf(value);
    }

    public static String abbreviate(double value) {
        if (value >= 1_000_000_000L) return round(value / 1_000_000_000.0, 1) + "B";
        if (value >= 1_000_000L)     return round(value / 1_000_000.0, 1) + "M";
        if (value >= 1_000L)         return round(value / 1_000.0, 1) + "K";
        return DECIMAL_FORMAT.format(value);
    }

    public static String toRoman(int number) {
        if (number <= 0 || number > 3999) return String.valueOf(number);
        int[]    values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] syms   = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                sb.append(syms[i]);
                number -= values[i];
            }
        }
        return sb.toString();
    }

    public static String formatDouble(double value) {
        return formatDouble(value, 2);
    }
}
