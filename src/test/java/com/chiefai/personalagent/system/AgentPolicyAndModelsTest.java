package com.chiefai.personalagent.system;

import com.chiefai.personalagent.action.application.ToolPolicyService;
import com.chiefai.personalagent.action.application.ToolRegistry;
import com.chiefai.personalagent.integration.openai.AgentModels;
import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

class AgentPolicyAndModelsTest {
    private final ToolPolicyService policy;
    AgentPolicyAndModelsTest() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.exists(anyString())).thenReturn(true);
        policy = new ToolPolicyService(registry);
    }
    @Test void systemToolsCannotBeGrantedToSpecialistsByAnAllowlist() {
        for (String specialist : List.of("health", "personal", "rucodel"))
            assertThatThrownBy(() -> policy.assertAllowed(specialist, "system.request_update", List.of("system.request_update")))
                    .isInstanceOf(SecurityException.class);
        policy.assertAllowed("manager", "system.request_update", List.of("system.request_update"));
    }
    @Test void specialistsCannotUseAnotherDomainOrInvokeAgents() {
        for (String tool : List.of("personal_tasks.create", "trello.create_card", "agent.invoke", "manager.run"))
            assertThatThrownBy(() -> policy.assertAllowed("health", tool, List.of(tool))).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> policy.assertAllowed("manager", "health.get_trends", List.of("health.get_trends")))
                .isInstanceOf(SecurityException.class);
    }
    @Test void perAgentModelsAndLegacyFallbackResolve() {
        var models = new AgentModels(new AgentModels.Manager("owner-manager", 1000),
                new AgentModels.Specialists("gpt-6-luna", 500, "work-model", "", "private-model"));
        var legacy = new PersonalAgentProperties.OpenAi(false, "", "old-model", "old-router", "", Duration.ofSeconds(30));
        assertThat(models.managerModel(legacy)).isEqualTo("owner-manager");
        assertThat(models.specialistModel("rucodel", legacy)).isEqualTo("work-model");
        assertThat(models.specialistModel("health", legacy)).isEqualTo("gpt-6-luna");
        assertThat(models.specialistModel("personal", legacy)).isEqualTo("private-model");
        var fallback = new AgentModels(new AgentModels.Manager("", 1000), new AgentModels.Specialists("", 500, "", "", ""));
        assertThat(fallback.managerModel(legacy)).isEqualTo("old-router");
        assertThat(fallback.specialistModel("personal", legacy)).isEqualTo("old-model");
    }
    @Test void invalidModelDoesNotAppearInError() {
        String secret = "secret token with spaces";
        assertThatThrownBy(() -> new AgentModels(new AgentModels.Manager(secret, 1000),
                new AgentModels.Specialists("", 500, "", "", "")))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(secret);
        assertThatThrownBy(() -> new AgentModels(new AgentModels.Manager("", 5),
                new AgentModels.Specialists("", 500, "", "", ""))).isInstanceOf(IllegalStateException.class);
    }
}
