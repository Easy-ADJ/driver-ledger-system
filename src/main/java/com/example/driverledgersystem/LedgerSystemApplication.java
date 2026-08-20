package com.example.driverledgersystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class LedgerSystemApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(LedgerSystemApplication.class, args);
    }
}