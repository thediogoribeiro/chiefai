package com.chiefai.personalagent.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final OwnerAccessConfigurationLoader ownerAccess;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public AuthController(AuthenticationManager authenticationManager, OwnerAccessConfigurationLoader ownerAccess) {
        this.authenticationManager = authenticationManager;
        this.ownerAccess = ownerAccess;
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, CsrfToken csrfToken) {
        String key = httpRequest.getRemoteAddr() + ":" + request.username();
        AttemptWindow window = attempts.computeIfAbsent(key, ignored -> new AttemptWindow());
        if (window.blocked()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            httpRequest.getSession(true);
            httpRequest.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            httpRequest.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            attempts.remove(key);
            return me(csrfToken);
        } catch (RuntimeException ex) {
            window.fail();
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(false);
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }

    @GetMapping("/me")
    public MeResponse me(CsrfToken csrfToken) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
        return new MeResponse(authenticated, authenticated ? authentication.getName() : null,
                authenticated ? "OWNER" : null, ownerAccess.access().configured(), csrfToken == null ? null : csrfToken.getToken());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record MeResponse(boolean authenticated, String username, String role, boolean accessConfigured, String csrfToken) {
    }

    private static final class AttemptWindow {
        private int failures;
        private Instant firstFailure = Instant.now();

        boolean blocked() {
            if (Instant.now().isAfter(firstFailure.plus(Duration.ofMinutes(10)))) {
                failures = 0;
                firstFailure = Instant.now();
            }
            return failures >= 5;
        }

        void fail() {
            if (Instant.now().isAfter(firstFailure.plus(Duration.ofMinutes(10)))) {
                failures = 0;
                firstFailure = Instant.now();
            }
            failures++;
        }
    }
}
