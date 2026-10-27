package com.chiefai.personalagent.agent.infrastructure;

import com.chiefai.personalagent.agent.application.KnowledgeRetriever;
import com.chiefai.personalagent.agent.domain.AgentDefinition;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class ClasspathKnowledgeRetriever implements KnowledgeRetriever {
    private final int maxChars;

    public ClasspathKnowledgeRetriever(PersonalAgentProperties properties) {
        this.maxChars = properties.knowledge() == null ? 6000 : properties.knowledge().maxChars();
    }

    @Override
    public String retrieve(AgentDefinition agent, String segment) {
        StringBuilder builder = new StringBuilder();
        if (agent.knowledgeScopes() == null) {
            return "";
        }
        for (String path : agent.knowledgeScopes()) {
            if (builder.length() >= maxChars) {
                break;
            }
            try {
                ClassPathResource resource = new ClassPathResource(path);
                String text = resource.getContentAsString(StandardCharsets.UTF_8);
                builder.append("\n\n## ").append(path).append("\n").append(text);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read knowledge resource " + path, e);
            }
        }
        return builder.length() > maxChars ? builder.substring(0, maxChars) : builder.toString();
    }
}
