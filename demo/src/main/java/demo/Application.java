package demo;

import org.activiti.engine.IdentityService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Josh Long
 * @author Joram Barrez
 */
@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {

    @Bean
    JavaDelegate delegate() {
        return delegate -> System.out.println("processInstanceId=" + delegate.getProcessInstanceId() + ".");
    }

    @Bean
    CommandLineRunner seedUsersAndGroups(IdentityService identityService, PhotoService photoService) {
        return args -> {
            // install groups & users
            Group group = identityService.newGroup("user");
            group.setName("users");
            group.setType("security-role");
            identityService.saveGroup(group);

            User joram = identityService.newUser("jbarrez");
            joram.setFirstName("Joram");
            joram.setLastName("Barrez");
            joram.setPassword("password");
            identityService.saveUser(joram);

            User josh = identityService.newUser("jlong");
            josh.setFirstName("Josh");
            josh.setLastName("Long");
            josh.setPassword("password");
            identityService.saveUser(josh);

            identityService.createMembership("jbarrez", "user");
            identityService.createMembership("jlong", "user");

            photoService.launchPhotoProcess("one", "two", "three");
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}


@Service
@Transactional
class PhotoService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final PhotoRepository photoRepository;

    @Autowired
    public PhotoService(RuntimeService runtimeService, TaskService taskService, PhotoRepository photoRepository) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.photoRepository = photoRepository;
    }

    public void processPhoto(Long photoId) {
        System.out.println("processing photo#" + photoId);
    }

    public void launchPhotoProcess(String... photoLabels) {
        List<Photo> photos = Arrays.asList(photoLabels).stream()
                .map(Photo::new)
                .collect(Collectors.toList())
                .stream().map(photoRepository::save).collect(Collectors.toList());

        Map<String, Object> procVars = new HashMap<>();
        procVars.put("photos", photos);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("dogeProcess", procVars);

        List<Execution> waitingExecutions = runtimeService.createExecutionQuery().activityId("wait").list();
        System.out.println("--> # executions = " + waitingExecutions.size());

        for (Execution execution : waitingExecutions) {
            runtimeService.signal(execution.getId());
        }

        Task reviewTask = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();

        taskService.complete(reviewTask.getId(), Collections.singletonMap("approved", true));

        long count = runtimeService.createProcessInstanceQuery().count();
        System.out.println("Proc count " + count);

    }
}

interface PhotoRepository extends JpaRepository<Photo, Long> {
}

@Entity
class Photo {

    @Id
    @GeneratedValue
    private Long id;

    Photo() {
    }

    Photo(String username) {
        this.username = username;
    }

    private String username;

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }
}