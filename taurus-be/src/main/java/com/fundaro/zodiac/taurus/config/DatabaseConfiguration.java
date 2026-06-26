package com.fundaro.zodiac.taurus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.fundaro.zodiac.taurus.repository")
@EnableTransactionManagement
public class DatabaseConfiguration {
}
