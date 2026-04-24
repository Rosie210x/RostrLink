package com.rostrlink;

import com.rostrlink.util.PasswordUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
        PasswordUtil passwordUtil = new PasswordUtil();
        System.out.println(passwordUtil.hash("12345678"));
    }
}

