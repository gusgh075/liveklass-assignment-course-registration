package com.futureschole.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class CourseApplication {

	public static void main(String[] args) {
		SpringApplication.run(CourseApplication.class, args);
	}

}
