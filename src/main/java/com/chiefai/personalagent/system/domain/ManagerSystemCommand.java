package com.chiefai.personalagent.system.domain;

import java.util.Arrays;
import java.util.Optional;

/** An exact command is required; quoted content and arbitrary arguments are never commands. */
public enum ManagerSystemCommand {
    STATUS("/system status", "system.get_status"),
    VERSION("/system version", "system.get_version"),
    UPDATE("/system update", "system.request_update"),
    UPDATE_STATUS("/system updates", "system.get_update_status");

    private final String text;
    private final String tool;
    ManagerSystemCommand(String text, String tool) { this.text = text; this.tool = tool; }
    public String tool() { return tool; }
    public static Optional<ManagerSystemCommand> parse(String text) {
        return text == null ? Optional.empty() : Arrays.stream(values()).filter(c -> c.text.equalsIgnoreCase(text.trim())).findFirst();
    }
}
