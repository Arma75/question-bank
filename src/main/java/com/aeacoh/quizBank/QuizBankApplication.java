package com.aeacoh.quizBank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class QuizBankApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuizBankApplication.class, args);
	}

}
