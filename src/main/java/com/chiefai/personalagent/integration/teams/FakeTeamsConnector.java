package com.chiefai.personalagent.integration.teams;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FakeTeamsConnector implements TeamsConnector {
    @Override
    public List<String> searchMessages(String query) {
        return List.of();
    }
}
