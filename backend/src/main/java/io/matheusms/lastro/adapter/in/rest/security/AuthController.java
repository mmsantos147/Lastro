package io.matheusms.lastro.adapter.in.rest.security;

import io.matheusms.lastro.domain.model.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session endpoints used by the frontend. Login ({@code POST /api/auth/login}) and logout
 * ({@code POST /api/auth/logout}) are handled by Spring Security filters (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    /**
     * Makes sure the {@code XSRF-TOKEN} cookie is issued. The frontend calls it before the first state-changing
     * request (including login) and then echoes the cookie value in the {@code X-XSRF-TOKEN} header.
     */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken(); // loading the deferred token writes the cookie
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new CurrentUserResponse(user.getUsername(), user.name(), user.role());
    }

    record CurrentUserResponse(String username, String name, UserRole role) {
    }
}
