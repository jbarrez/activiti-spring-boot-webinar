package demo;

import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {

    @Bean
    JavaDelegate delegate() {
        return delegate -> System.out.println("processInstanceId=" + delegate.getProcessInstanceId() + ".");
    }

    @Bean
    CommandLineRunner seedUsersAndGroups(RuntimeService runtimeService,
                                         RepositoryService repositoryService,
                                         IdentityService identityService) {
        return args -> {
            // install groups & users
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

            // launch a process definition
            String waiterProcessDefinition = "waiter";
            Map<String, Object> vars = new HashMap<String, Object>();
            vars.put("photoId", 232);
            runtimeService.startProcessInstanceByKey(waiterProcessDefinition, vars);
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}