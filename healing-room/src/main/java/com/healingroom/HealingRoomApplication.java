package com.healingroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealingRoomApplication {

	public static void main(String[] args) {
		SpringApplication.run(HealingRoomApplication.class, args);
	}

}
