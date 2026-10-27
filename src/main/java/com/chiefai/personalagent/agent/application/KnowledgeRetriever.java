package com.chiefai.personalagent.agent.application;

import com.chiefai.personalagent.agent.domain.AgentDefinition;

public interface KnowledgeRetriever {
    String retrieve(AgentDefinition agent, String segment);
}
