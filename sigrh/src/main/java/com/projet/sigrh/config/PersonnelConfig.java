package com.projet.sigrh.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersonnelConfig {
    @Bean
    public Clock personnelClock() { return Clock.system(ZoneId.of("Indian/Antananarivo")); }
}
