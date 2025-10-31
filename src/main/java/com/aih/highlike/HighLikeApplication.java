package com.aih.highlike;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.aih.highlike.mapper")
@EnableScheduling
public class HighLikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighLikeApplication.class, args);
    }

}
