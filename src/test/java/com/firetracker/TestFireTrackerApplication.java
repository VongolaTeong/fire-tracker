package com.firetracker;

import org.springframework.boot.SpringApplication;

public class TestFireTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(FireTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
