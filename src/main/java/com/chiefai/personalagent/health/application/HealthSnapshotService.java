package com.chiefai.personalagent.health.application;

import com.chiefai.personalagent.health.infrastructure.DailyHealthSnapshotEntity;
import com.chiefai.personalagent.health.infrastructure.DailyHealthSnapshotJpaRepository;
import com.chiefai.personalagent.shared.Jsonb;
import com.chiefai.personalagent.shared.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

@Service
public class HealthSnapshotService {
    public static final String DISCLAIMER = "Esta análise é informativa e não constitui diagnóstico médico.";
    private final DailyHealthSnapshotJpaRepository snapshots;

    public HealthSnapshotService(DailyHealthSnapshotJpaRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Transactional
    public HealthSnapshotDto upsert(LocalDate date, String source, JsonNode payload) {
        DailyHealthSnapshotEntity entity = snapshots.findByDateAndSource(date, source).orElseGet(() -> new DailyHealthSnapshotEntity(date, source));
        entity.update(intOrNull(payload, "sleepScore"), intOrNull(payload, "readinessScore"), intOrNull(payload, "activityScore"),
                decimalOrNull(payload, "restingHeartRate"), decimalOrNull(payload, "averageHrv"), intOrNull(payload, "sleepDurationMinutes"),
                payload == null ? Jsonb.object() : payload);
        return toDto(snapshots.save(entity));
    }

    @Transactional(readOnly = true)
    public HealthSnapshotDto get(LocalDate date, String source) {
        return snapshots.findByDateAndSource(date, source).map(this::toDto)
                .orElseThrow(() -> new NotFoundException("Health snapshot not found for " + date + " and " + source));
    }

    @Transactional(readOnly = true)
    public List<HealthSnapshotDto> list(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return snapshots.findByDateBetweenOrderByDateAsc(start, end).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public HealthTrendDto trends(LocalDate from, LocalDate to) {
        List<HealthSnapshotDto> rows = list(from, to);
        Map<String, HealthTrendDto.MetricTrend> metrics = new LinkedHashMap<>();
        metrics.put("sleepScore", trend(rows, s -> num(s.sleepScore())));
        metrics.put("readinessScore", trend(rows, s -> num(s.readinessScore())));
        metrics.put("activityScore", trend(rows, s -> num(s.activityScore())));
        metrics.put("restingHeartRate", trend(rows, s -> num(s.restingHeartRate())));
        metrics.put("averageHrv", trend(rows, s -> num(s.averageHrv())));
        return new HealthTrendDto(from, to, rows.size(), metrics, DISCLAIMER);
    }

    private HealthTrendDto.MetricTrend trend(List<HealthSnapshotDto> rows, Function<HealthSnapshotDto, Double> getter) {
        List<Double> values = rows.stream().map(getter).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return new HealthTrendDto.MetricTrend(null, null, null, "stable");
        }
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        String direction = values.size() < 2 ? "stable" : values.get(values.size() - 1) > values.get(0) ? "up" : values.get(values.size() - 1) < values.get(0) ? "down" : "stable";
        return new HealthTrendDto.MetricTrend(avg, min, max, direction);
    }

    private HealthSnapshotDto toDto(DailyHealthSnapshotEntity e) {
        return new HealthSnapshotDto(e.getId(), e.getDate(), e.getSource(), e.getSleepScore(), e.getReadinessScore(), e.getActivityScore(),
                e.getRestingHeartRate(), e.getAverageHrv(), e.getSleepDurationMinutes(), e.getAdditionalPayload(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).decimalValue() : null;
    }

    private static Double num(Number number) {
        return number == null ? null : number.doubleValue();
    }
}
