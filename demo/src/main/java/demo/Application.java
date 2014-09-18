package demo;

import java.io.InputStream;

import javax.jms.ConnectionFactory;
import javax.jms.MessageListener;

import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.activiti.image.impl.DefaultProcessDiagramGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

	@Autowired
	private ConnectionFactory connectionFactory;

	@Autowired
	private TaskService taskService;

	@Bean
	public DefaultMessageListenerContainer messageListener() {
		DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
		container.setConnectionFactory(this.connectionFactory);
		container.setDestinationName("testQueue");

		MessageListener ml = msg -> {
			System.out.println(msg.toString());

			Task task = taskService.newTask();
			task.setName("Shouldn't exist");
			taskService.saveTask(task);

			throw new RuntimeException();
		};

		container.setMessageListener(ml);

		return container;
	}

	@Bean
	TransactionTemplate template(
	        PlatformTransactionManager platformTransactionManager) {
		return new TransactionTemplate(platformTransactionManager);
	}

	@Bean
	CommandLineRunner initUsers(TransactionTemplate template,
	        IdentityService identityService) {
		return args -> {

			Group group = identityService.newGroup("user");
			group.setName("users");
			group.setType("security-role");
			identityService.saveGroup(group);

			User joram = identityService.newUser("jbarrez");
			joram.setFirstName("Joram");
			joram.setLastName("Barrez");
			joram.setPassword("joram");
			identityService.saveUser(joram);

			// Testing XA
			template.execute(status -> {

				User josh = identityService.newUser("jlong");
				josh.setFirstName("Josh");
				josh.setLastName("Long");
				josh.setPassword("josh");
				identityService.saveUser(josh);

				identityService.createMembership("jlong", "user");

				status.setRollbackOnly();
				return null;
			});

			identityService.createMembership("jbarrez", "user");
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}

@RestController
class ActivitiDiagramController {
	@Autowired
	RepositoryService repositoryService;

	@RequestMapping(value="/processes/diagrams/{pd}", produces= MediaType.IMAGE_PNG_VALUE )
	Resource renderProcessDiagram(@PathVariable String pd) {
		ProcessDefinition processDefinition = repositoryService
		        .createProcessDefinitionQuery().processDefinitionKey(pd).singleResult();
		ProcessDiagramGenerator processDiagramGenerator = new DefaultProcessDiagramGenerator();
		InputStream is = processDiagramGenerator
		        .generatePngDiagram(repositoryService
		                .getBpmnModel(processDefinition.getId()));

		return new InputStreamResource(is);
		
	}

}

@Component
class PhotoService {

	@Autowired
	private JmsTemplate jmsTemplate;

	public void processPhoto(long photoId) {
		this.jmsTemplate.convertAndSend("testQueue",
		        "about to process photo # " + photoId);
	}
}
