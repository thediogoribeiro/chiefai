package com.chiefai.personalagent.access;

import com.chiefai.personalagent.shared.PersonalAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OwnerAccessConfigurationLoader {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");
    private final YAMLMapper mapper = new YAMLMapper();
    private final PersonalAgentProperties properties;
    private final Environment environment;
    private OwnerAccess access;

    public OwnerAccessConfigurationLoader(PersonalAgentProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void load() {
        try {
            String yaml = configuredFile() == null
                    ? new ClassPathResource("access/owner-access.example.yml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                    : java.nio.file.Files.readString(configuredFile().toPath());
            JsonNode owner = mapper.readTree(resolvePlaceholders(yaml)).path("owner");
            String username = text(owner, "username");
            String passwordHash = text(owner, "passwordHash");
            JsonNode telegram = owner.path("telegram");
            String userId = text(telegram, "allowedUserId");
            String chatId = text(telegram, "allowedChatId");
            String phone = text(telegram, "declaredPhoneNumber");
            boolean configured = present(username) && present(passwordHash);
            access = new OwnerAccess(configured, username, passwordHash, new OwnerAccess.Telegram(userId, chatId, phone));
            validateProduction();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load ChiefAI owner access configuration", e);
        }
    }

    public OwnerAccess access() {
        return access;
    }

    private File configuredFile() {
        String value = properties.access() == null ? null : properties.access().file();
        if (value == null || value.isBlank()) {
            return null;
        }
        File file = new File(value);
        if (!file.isFile()) {
            throw new IllegalStateException("Configured ChiefAI access file was not found");
        }
        return file;
    }

    private void validateProduction() {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("production");
        if (!production) {
            return;
        }
        if (!access.configured()
                || !present(access.telegram().allowedUserId())
                || !present(access.telegram().allowedChatId())) {
            throw new IllegalStateException("Production profile requires complete ChiefAI owner access configuration");
        }
    }

    private String resolvePlaceholders(String yaml) {
        Matcher matcher = PLACEHOLDER.matcher(yaml);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = environment.getProperty(matcher.group(1));
            if (value == null) {
                value = matcher.group(2) == null ? "" : matcher.group(2);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
