package com.chiefai.personalagent.access;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerAccessConfigurationLoaderTest {
    @Test
    void productionFailsWhenOwnerConfigurationIsIncomplete() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        OwnerAccessConfigurationLoader loader = new OwnerAccessConfigurationLoader(properties(""), environment);

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to load ChiefAI owner access configuration")
                .hasRootCauseMessage("Production profile requires complete ChiefAI owner access configuration");
    }

    @Test
    void configurationErrorsDoNotExposeConfiguredFilePath() {
        String sensitivePath = "/tmp/chiefai-owner-secret-location.yml";
        OwnerAccessConfigurationLoader loader = new OwnerAccessConfigurationLoader(properties(sensitivePath), new MockEnvironment());

        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to load ChiefAI owner access configuration")
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(error.toString()).doesNotContain(sensitivePath));
    }

    private PersonalAgentProperties properties(String accessFile) {
        return new PersonalAgentProperties(null, null, null, null, new PersonalAgentProperties.Access(accessFile),
                null, null, null, null, null, null, null);
    }
}
