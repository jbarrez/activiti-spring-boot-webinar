package demo;

import org.activiti.engine.IdentityService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.identity.Group;
import org.activiti.engine.identity.User;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.spring.integration.ActivitiInboundGateway;
import org.activiti.spring.integration.IntegrationActivityBehavior;
import org.h2.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

    @Configuration
    static class SimpleMvcConfiguration extends WebMvcConfigurerAdapter {
        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            registry.addViewController("/").setViewName("upload");
        }
    }

    @Bean
    IntegrationActivityBehavior activitiDelegate(ActivitiInboundGateway activitiInboundGateway) {
        return new IntegrationActivityBehavior(activitiInboundGateway);
    }

    @Bean
    ActivitiInboundGateway inboundGateway(ProcessEngine processEngine) {
        return new ActivitiInboundGateway(processEngine, "processed", "userId", "photo", "photos");
    }

    @Bean
    IntegrationFlow inboundProcess(ActivitiInboundGateway activitiInboundGateway) {
        return IntegrationFlows
                .from(activitiInboundGateway)
                .handle(
                        new GenericHandler<ActivityExecution>() {
                            @Override
                            public Object handle(ActivityExecution execution, Map<String, Object> headers) {

                                System.out.println("handling execution " + headers.toString());

                                return MessageBuilder.withPayload(execution)
                                        .setHeader("processed", (Object) true)
                                        .copyHeaders(headers).build();
                            }
                        }
                )
                .get();
    }


    @Bean
    CommandLineRunner init(IdentityService identityService) {
        return new CommandLineRunner() {
            @Override
            public void run(String... strings) throws Exception {

                // install groups & users
                Group photoReviewersGroup = group("photoReviewers");
                Group usersGroup = group("users");
                Group adminGroup = group("admins");

                User joram = user("jbarrez", "Joram", "Barrez");
                identityService.createMembership(joram.getId(), photoReviewersGroup.getId());
                identityService.createMembership(joram.getId(), usersGroup.getId());
                identityService.createMembership(joram.getId(), adminGroup.getId());

                User josh = user("jlong", "Josh", "Long");
                identityService.createMembership(josh.getId(), photoReviewersGroup.getId());
                identityService.createMembership(josh.getId(), usersGroup.getId());
            }

            private User user(String userName, String f, String l) {
                User u = identityService.newUser(userName);
                u.setFirstName(f);
                u.setLastName(l);
                u.setPassword("password");
                identityService.saveUser(u);
                return u;
            }

            private Group group(String groupName) {
                Group group = identityService.newGroup(groupName);
                group.setName(groupName);
                group.setType("security-role");
                identityService.saveGroup(group);
                return group;
            }
        };

    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

interface PhotoRepository extends JpaRepository<Photo, Long> {
}

@Entity
class Photo {

    @Id
    @GeneratedValue
    private Long id;

    private String userId;

    private boolean processed;

    public boolean isProcessed() {
        return processed;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    // jpa
    Photo() {
    }

    public Photo(String userId) {
        this.userId = userId;
    }

    public Photo(String userId, boolean processed) {
        this.userId = userId;
        this.processed = processed;
    }
}

@Service
@Transactional
class PhotoService {

    @Autowired
    private TaskService taskService;

    @Value("file://#{ systemProperties['user.home'] }/Desktop/uploads")
    private Resource uploadDirectory;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private PhotoRepository photoRepository;

    @PostConstruct
    public void beforeService() throws Exception {
        File uploadDir = this.uploadDirectory.getFile();
        Assert.isTrue(uploadDir.exists() || uploadDir.mkdirs(), "the " + uploadDir.getAbsolutePath() + " folder must exist!");
    }

    protected void writePhoto(Photo photo, InputStream inputStream) {
        try {
            try (InputStream input = inputStream;
                 FileOutputStream output = new FileOutputStream(new File(this.uploadDirectory.getFile(), Long.toString(photo.getId())))) {
                IOUtils.copy(input, output);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream readPhoto(Photo photo) {
        try {
            return new FileInputStream( new File(this.uploadDirectory.getFile(), Long.toString(photo.getId())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Photo createPhoto(Long userId, InputStream bytesForImage) {
        Photo photo = this.photoRepository.save(new Photo(Long.toString(userId), false));
        writePhoto(photo, bytesForImage);
        return photo;
    }

    public ProcessInstance launchPhotoProcess(List<Photo> photos) {
        return runtimeService.startProcessInstanceByKey( "photoProcess", Collections.singletonMap("photos", photos));
    }
}


@Controller
class PhotoMvcController {

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private TaskService taskService;

    public static final Long USER_ID = 24242L; ///TODO fixme by plucking this from the Spring Security Principal

    @RequestMapping(method = RequestMethod.POST, value = "/upload")
    ResponseEntity<?> upload(MultipartHttpServletRequest request) {

        Optional.ofNullable(request.getMultiFileMap()).ifPresent(m -> {

            // gather all the MFs in one collection
            List<MultipartFile> multipartFiles = new ArrayList<>();
            m.values().forEach((t) -> {
                multipartFiles.addAll(t);
            });

            // convert them all into `Photo`s
            List<Photo> photos = multipartFiles.stream().map(f -> {
                try {
                    return this.photoService.createPhoto(USER_ID, f.getInputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

            System.out.println("started photo process w/ process instance ID: " +
                    this.photoService.launchPhotoProcess(photos).getId());

        });
        return new ResponseEntity<>(HttpStatus.ACCEPTED);
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    Resource image(@PathVariable Long id) {
        return new InputStreamResource(this.photoService.readPhoto(this.photoRepository.findOne(id)));
    }

    @RequestMapping(value = "/approve", method = RequestMethod.POST)
    String approveTask(@RequestParam String taskId, @RequestParam String approved) {
        boolean isApproved = !(approved == null || approved.trim().equals("") || approved.toLowerCase().contains("f") || approved.contains("0"));
        this.taskService.complete(taskId, Collections.singletonMap("approved", isApproved));
        return "approve";
    }

    @RequestMapping(value = "/approve", method = RequestMethod.GET)
    String setupApprovalPage(Model model) {
        List<Task> outstandingTasks = taskService.createTaskQuery()
                .taskCandidateGroup("photoReviewers")
                .list();
        if (0 < outstandingTasks.size()) {
            Task task = outstandingTasks.iterator().next();
            model.addAttribute("task", task);
            List<Photo> photos = (List<Photo>) taskService.getVariable(task.getId(), "photos");
            model.addAttribute("photos", photos);
        }
        return "approve";
    }


}