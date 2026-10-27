package com.chiefai.personalagent.health.application;

import java.time.LocalDate;
import java.util.Map;

public record HealthTrendDto(LocalDate from, LocalDate to, int samples, Map<String, MetricTrend> metrics, String disclaimer) {
    public record MetricTrend(Double average, Double min, Double max, String trend) {
    }
}
