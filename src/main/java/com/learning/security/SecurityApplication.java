package com.learning.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
		scanBasePackages = "com.learning.security",
		exclude = {RedisRepositoriesAutoConfiguration.class}
)
@EnableScheduling
public class SecurityApplication {

	private static final String DEVELOPER = "M. Yousuf (https://github.com/yousuf-git)";

	public static void main(String[] args) {
		SpringApplication.run(SecurityApplication.class, args);
		System.out.println("Welcome Back M.Y., Auth Forge is running now... ! Built by " + DEVELOPER);

	}

}
