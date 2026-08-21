package com.example.driverledgersystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Driver Ledger System 애플리케이션의 실행 진입점입니다.
 */
@EnableJpaAuditing
@SpringBootApplication
public class LedgerSystemApplication
{
    /**
     * Spring Boot 애플리케이션을 실행합니다.
     *
     * @param args 애플리케이션 실행 인자
     */
    public static void main(String[] args)
    {
        SpringApplication.run(
                LedgerSystemApplication.class,
                args
        );
    }
}