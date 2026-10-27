package com.chiefai.personalagent.integration.teams;

import com.chiefai.personalagent.integration.NamedConnector;

import java.util.List;

public interface TeamsConnector extends NamedConnector {
    List<String> searchMessages(String query);

    @Override
    default String connectorName() { return "teams"; }
}
