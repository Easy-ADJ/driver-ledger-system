package com.example.driverledgersystem.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import javax.sql.DataSource;


@Configuration
@EnableJpaRepositories(
        basePackages = "com.example.driverledgersystem.repository",
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager"
)
public class PrimaryDataSourceConfig
{
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties()
    {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource primaryDataSource()
    {
        return primaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }
}