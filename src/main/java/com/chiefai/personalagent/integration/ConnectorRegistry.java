package com.chiefai.personalagent.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ConnectorRegistry {
    private final Set<String> connectorNames;

    public ConnectorRegistry(Collection<NamedConnector> connectors) {
        this.connectorNames = connectors.stream().map(NamedConnector::connectorName).collect(Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void validateRequiredConnectorsExist() {
        Set<String> required = Set.of("telegram", "whatsapp", "gmail", "google_sheets", "google_calendar", "teams", "trello", "oura");
        if (!connectorNames.containsAll(required)) {
            throw new IllegalStateException("Missing connectors: " + required.stream().filter(name -> !connectorNames.contains(name)).toList());
        }
    }

    public Map<String, Boolean> inventory() {
        return connectorNames.stream().collect(Collectors.toMap(name -> name, name -> true));
    }

    public Map<String, Object> status() {
        return connectorNames.stream().sorted()
                .collect(Collectors.toMap(name -> name, name -> Map.of("available", true, "mode", "configured-or-fake"),
                        (left, right) -> left, java.util.LinkedHashMap::new));
    }
}
