package com.example.driverledgersystem.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import javax.sql.DataSource;

// 로그인 DB — 기사 정보 조회 전용 DataSource
@Configuration
@EnableJpaRepositories(
        basePackages = "com.example.driverledgersystem.auth.repository",
        entityManagerFactoryRef = "authEntityManagerFactory",
        transactionManagerRef = "authTransactionManager"
)
public class AuthDataSourceConfig
{
    @Bean
    @ConfigurationProperties("auth.datasource")
    public DataSourceProperties authDataSourceProperties()
    {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource authDataSource()
    {
        return authDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }
}