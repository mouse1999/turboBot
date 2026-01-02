package com.mouse.bet;

import com.mouse.bet.client.BreakingBetClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;

@SpringBootApplication
public class TurboBotApplication {

	public static void main(String[] args) {

//		SpringApplication.run(TurboBotApplication.class, args);
		ConfigurableApplicationContext context = SpringApplication.run(TurboBotApplication.class, args);
		BreakingBetClient bet = context.getBean(BreakingBetClient.class);
        try {
            int count = 0;
            while(count < 15) {
                String response = bet.fetchLiveArbs();
                System.out.println(response);
                count++;
                Thread.sleep(2000);
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }


    }

}
