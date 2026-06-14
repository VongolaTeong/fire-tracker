package com.firetracker.common;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared application beans. */
@Configuration
public class AppConfig {

    /**
     * The clock used to resolve "today" for time-dependent analytics (the XIRR terminal date,
     * CAGR horizon). A bean rather than {@code LocalDate.now()} so tests can pin it to a fixed
     * instant; {@code @ConditionalOnMissingBean} lets a test-supplied clock take precedence.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
