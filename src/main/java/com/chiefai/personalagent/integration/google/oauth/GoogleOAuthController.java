package com.chiefai.personalagent.integration.google.oauth;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/google")
public class GoogleOAuthController {
    private final GoogleOAuthService service;

    public GoogleOAuthController(GoogleOAuthService service) {
        this.service = service;
    }

    @PostMapping("/authorize")
    Map<String, String> authorize() {
        return service.authorizationUrl();
    }

    @GetMapping("/callback")
    Map<String, Object> callback(@RequestParam String code) {
        return service.complete(code);
    }

    @GetMapping("/status")
    Map<String, Object> status() {
        return service.status();
    }
}
