package demo;

import org.activiti.engine.IdentityService;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {
	
	@Bean 
	CommandLineRunner initUsers( IdentityService  identityService) {
	 return args ->{
		 
		 Group group = identityService.newGroup("user");
		 group.setName("users");
		 group.setType("security-role");
	     identityService.saveGroup(group);
		 
		 User joram = identityService.newUser("jbarrez");
		 joram.setFirstName("Joram");
		 joram.setLastName("Barrez");
		 joram.setPassword("joram");
		 identityService.saveUser(joram);
		 
		 User josh = identityService.newUser("jlong");
		 josh.setFirstName("Josh");
		 josh.setLastName("Long");
		 josh.setPassword("josh");
		 identityService.saveUser(josh);
		 
		 identityService.createMembership("jbarrez", "user");
		 identityService.createMembership("jlong", "user");
	 };
 }

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}

@Component
class PhotoService {

	public void processPhoto(long photoId) {
		System.out.println("about to process photo # " + photoId);
	}
}
