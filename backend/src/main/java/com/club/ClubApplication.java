package com.club;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 校园社团综合管理系统启动类。
 * 分层约定(见设计说明书 2.1):
 *   Controller -> Service -> Mapper -> MySQL
 */
@SpringBootApplication
@MapperScan("com.club.mapper")
@EnableTransactionManagement
public class ClubApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClubApplication.class, args);
    }
}
