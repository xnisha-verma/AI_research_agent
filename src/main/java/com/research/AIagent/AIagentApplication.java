package com.research.AIagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AIagentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AIagentApplication.class, args);
	}

}
