package com.no8do.api.agent;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentPresenceProperties.class)
public class AgentPresenceConfiguration {
    @Bean
    Clock agentPresenceClock() {
        return Clock.systemUTC();
    }
}
