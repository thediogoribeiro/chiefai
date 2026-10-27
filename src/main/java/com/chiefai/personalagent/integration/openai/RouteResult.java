package com.chiefai.personalagent.integration.openai;

import java.util.List;

public record RouteResult(List<Route> routes, boolean clarificationRequired, String clarificationQuestion) {
    public record Route(String agentId, String segment, double confidence, String reason) {
    }
}
