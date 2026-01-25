package com.mouse.bet;

import com.mouse.bet.client.BreakingBetClient;
//import com.mouse.bet.window.MSport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class TurboBotApplication {

	public static void main(String[] args) {

		SpringApplication.run(TurboBotApplication.class, args);
//		ConfigurableApplicationContext context = SpringApplication.run(TurboBotApplication.class, args);
//		MSport mSport = context.getBean(MSport.class);
//		mSport.run();
    }

}
