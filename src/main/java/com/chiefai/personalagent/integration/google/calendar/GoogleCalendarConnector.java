package com.chiefai.personalagent.integration.google.calendar;

import com.chiefai.personalagent.integration.NamedConnector;

import java.time.OffsetDateTime;

public interface GoogleCalendarConnector extends NamedConnector {
    String createEvent(String calendarId, String title, OffsetDateTime startsAt, OffsetDateTime endsAt, String idempotencyKey);

    @Override
    default String connectorName() {
        return "google_calendar";
    }
}
