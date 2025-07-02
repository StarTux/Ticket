package com.winthier.ticket;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;

public final class Util {
    private Util() { }

    public static String formatDate(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(time));
        DateFormatSymbols symbols = DateFormatSymbols.getInstance();
        return String.format("%s %02d %d, %02d:%02d",
                             symbols.getShortMonths()[cal.get(Calendar.MONTH)],
                             cal.get(Calendar.DAY_OF_MONTH),
                             cal.get(Calendar.YEAR),
                             cal.get(Calendar.HOUR_OF_DAY),
                             cal.get(Calendar.MINUTE));
    }

    public static String formatInterval(long interval) {
        final int seconds = (int) (interval / 1000);
        final int minutes = seconds / 60;
        final int hours = minutes / 60;
        int days = hours / 24;
        // display hours, minutes and seconds if less than a day ago
        if (days == 0) {
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
        // calculate years and months
        final int years = days / 365;
        days -= years * 365;
        final int months = days / 30;
        days -= months * 30;
        // build the string
        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append("y");
        if (months > 0) sb.append(months).append("m");
        if (days > 0) sb.append(days).append("d");
        return sb.toString();
    }
}
