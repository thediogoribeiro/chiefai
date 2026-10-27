package com.chiefai.personalagent.integration.google.calendar;

import org.springframework.stereotype.Component;

@Component
public class FakeGoogleCalendarConnector implements GoogleCalendarConnector {
    @Override
    public String createEvent(String calendarId, String title, java.time.OffsetDateTime startsAt, java.time.OffsetDateTime endsAt, String idempotencyKey) {
        return "fake-calendar-event-" + Math.abs(idempotencyKey.hashCode());
    }
}
