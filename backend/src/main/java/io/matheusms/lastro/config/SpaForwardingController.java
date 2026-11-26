package io.matheusms.lastro.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * In production the frontend build is served by Spring Boot from {@code classpath:/static}. Client-side routes
 * (e.g. {@code /analises/5}) have no file behind them, so they are forwarded to {@code index.html} and React Router
 * takes over. Only paths up to three segments whose last segment has no dot are matched, so files such as
 * {@code /assets/index-abc.js} are served as they are; the API, MCP and actuator paths are never matched.
 */
@Controller
class SpaForwardingController {

    private static final String ROOT = "{root:^(?!api$|mcp$|oauth$|actuator$|assets$)[^.]*}";
    private static final String SEGMENT = "{segment:[^.]*}";
    private static final String LEAF = "{leaf:[^.]*}";

    @GetMapping({"/" + ROOT, "/" + ROOT + "/" + LEAF, "/" + ROOT + "/" + SEGMENT + "/" + LEAF})
    String forward() {
        return "forward:/index.html";
    }
}
