package github.salemby.tododevops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class SalemTodoDevOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SalemTodoDevOpsApplication.class, args);
	}

}
