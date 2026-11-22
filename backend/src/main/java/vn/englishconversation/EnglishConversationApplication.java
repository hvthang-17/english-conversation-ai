package vn.englishconversation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// AUTH-01 owns registration and controlled admin bootstrap; never generate a default account.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EnglishConversationApplication {

  public static void main(String[] args) {
    SpringApplication.run(EnglishConversationApplication.class, args);
  }
}
