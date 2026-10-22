package com.jobtracker;

import com.jobtracker.tailoring.Tailoring;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes the tailoring module available to the app. The module itself knows nothing about Spring. */
@Configuration
public class TailoringConfig {

    @Bean
    Tailoring tailoring() {
        return Tailoring.load();
    }
}
