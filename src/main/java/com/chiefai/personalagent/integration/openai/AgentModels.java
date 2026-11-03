package com.chiefai.personalagent.integration.openai;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "personal-agent.models")
public record AgentModels(Manager manager, Specialists specialists) {
    public record Manager(String model, String maxOutputTokens) {
        @ConstructorBinding public Manager { }
        public Manager(String model, int tokens) { this(model, Integer.toString(tokens)); }
        public int outputTokens() { return tokens(maxOutputTokens); }
    }
    public record Specialists(String defaultModel, String maxOutputTokens, String rucodel, String health, String personal) {
        @ConstructorBinding public Specialists { }
        public Specialists(String model, int tokens, String rucodel, String health, String personal) { this(model, Integer.toString(tokens), rucodel, health, personal); }
        public int outputTokens() { return tokens(maxOutputTokens); }
    }
    public AgentModels {
        if (manager == null || specialists == null) throw new IllegalStateException("Missing agent model configuration");
        validateOptionalModel(manager.model());
        validateOptionalModel(specialists.defaultModel());
        validateOptionalModel(specialists.rucodel());
        validateOptionalModel(specialists.health());
        validateOptionalModel(specialists.personal());
        manager.outputTokens();
        specialists.outputTokens();
    }
    public String managerModel(PersonalAgentProperties.OpenAi legacy) {
        String fallback = legacy != null && present(legacy.routerModel()) ? legacy.routerModel() : defaultModel(legacy);
        return checked(present(manager.model()) ? manager.model() : fallback);
    }
    public String specialistModel(String id, PersonalAgentProperties.OpenAi legacy) {
        String override = switch (id) {
            case "rucodel" -> specialists.rucodel();
            case "health" -> specialists.health();
            case "personal" -> specialists.personal();
            default -> throw new IllegalArgumentException("Unknown specialist");
        };
        return checked(present(override) ? override : defaultModel(legacy));
    }
    private String defaultModel(PersonalAgentProperties.OpenAi legacy) {
        return present(specialists.defaultModel()) ? specialists.defaultModel()
                : legacy != null && present(legacy.model()) ? legacy.model() : "gpt-6-luna";
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static void validateOptionalModel(String value) { if (present(value)) checked(value); }
    private static String checked(String value) {
        if (!present(value) || value.startsWith("sk-") || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
            throw new IllegalStateException("Invalid OpenAI model identifier");
        return value;
    }
    private static int tokens(String value) {
        int count;
        try { count = Integer.parseInt(value); }
        catch (Exception ex) { throw new IllegalStateException("Invalid OpenAI output token limit"); }
        if (count < 128 || count > 16384) throw new IllegalStateException("OpenAI output token limit must be between 128 and 16384");
        return count;
    }
}
