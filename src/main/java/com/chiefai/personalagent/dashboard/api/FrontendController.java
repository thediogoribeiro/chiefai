package com.chiefai.personalagent.dashboard.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    @GetMapping({"/", "/login", "/finance", "/health", "/future", "/settings"})
    public String index() {
        return "forward:/index.html";
    }
}
