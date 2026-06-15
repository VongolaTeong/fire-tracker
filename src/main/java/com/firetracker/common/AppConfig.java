package com.firetracker.common;

import com.firetracker.marketdata.MarketDataProperties;
import com.firetracker.projection.ProjectionProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/** Shared application beans. */
@Configuration
@EnableConfigurationProperties({MarketDataProperties.class, ProjectionProperties.class})
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

    /**
     * A fresh {@link RestClient.Builder} per injection point, used by the market-data providers
     * to build their HTTP clients. Prototype-scoped (like Spring Boot's own auto-configured
     * builder) so each provider sets its own base URL without sharing mutable state.
     */
    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
