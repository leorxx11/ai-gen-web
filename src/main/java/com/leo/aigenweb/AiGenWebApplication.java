package com.leo.aigenweb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.leo.aigenweb.mapper")
public class AiGenWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGenWebApplication.class, args);
    }

}
