package com.propertyhub.offline.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeProvider {

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String TIME_PATTERN = "hh:mm a";

    public Date now() {
        return new Date();
    }

    public String formatLocalDate(Date date) {
        return formatLocalDate(date, TimeZone.getDefault());
    }

    public String formatLocalDate(Date date, TimeZone timeZone) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.US);
        dateFormat.setTimeZone(timeZone);
        return dateFormat.format(date);
    }

    public String formatLocalTime(Date date) {
        return formatLocalTime(date, TimeZone.getDefault());
    }

    public String formatLocalTime(Date date, TimeZone timeZone) {
        SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_PATTERN, Locale.US);
        timeFormat.setTimeZone(timeZone);
        return timeFormat.format(date);
    }
}
