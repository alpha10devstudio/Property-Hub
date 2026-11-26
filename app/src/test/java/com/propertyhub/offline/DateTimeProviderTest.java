package com.propertyhub.offline;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

public class DateTimeProviderTest {

    @Test
    public void usesLocalTimezoneAcrossMidnightInsteadOfUtc() {
        DateTimeProvider provider = new DateTimeProvider();
        TimeZone dhakaTz = TimeZone.getTimeZone("Asia/Dhaka"); // UTC+6

        // 2026-11-26 18:30 UTC is 2026-11-27 00:30 AM in Asia/Dhaka
        Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcCal.set(2026, Calendar.NOVEMBER, 26, 18, 30, 0);
        utcCal.set(Calendar.MILLISECOND, 0);

        String localDate = provider.formatLocalDate(utcCal.getTime(), dhakaTz);
        String localTime = provider.formatLocalTime(utcCal.getTime(), dhakaTz);

        assertEquals("2026-11-27", localDate);
        assertEquals("12:30 AM", localTime);
    }
}
