package com.chiefai.personalagent.access;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class OwnerAuthorization {
    private OwnerAuthorization() { }
    public static void requireHumanOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_OWNER"))
                || authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL"))) {
            throw new SecurityException("A human owner session is required");
        }
    }
}
