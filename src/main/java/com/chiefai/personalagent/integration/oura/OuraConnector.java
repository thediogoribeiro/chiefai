package com.chiefai.personalagent.integration.oura;

import com.chiefai.personalagent.integration.NamedConnector;
import com.fasterxml.jackson.databind.JsonNode;

public interface OuraConnector extends NamedConnector {
    JsonNode readMetrics();

    @Override
    default String connectorName() { return "oura"; }
}
