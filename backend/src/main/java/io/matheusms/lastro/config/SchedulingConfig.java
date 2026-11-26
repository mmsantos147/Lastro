package io.matheusms.lastro.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduled jobs; tests turn them off with {@code lastro.scheduling.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "lastro.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
