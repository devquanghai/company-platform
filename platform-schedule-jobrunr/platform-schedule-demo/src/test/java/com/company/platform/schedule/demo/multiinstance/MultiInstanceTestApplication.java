package com.company.platform.schedule.demo.multiinstance;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
public class MultiInstanceTestApplication {

    @Bean
    ProbeJobRequestHandler probeJobRequestHandler() {
        return new ProbeJobRequestHandler();
    }
}
