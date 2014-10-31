package demo;

import doge.photo.DogePhotoManipulator;
import doge.photo.PhotoManipulator;

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
import org.apache.catalina.security.SecurityConfig;
import org.h2.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
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
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;

@Configuration
@ComponentScan
@EnableAutoConfiguration
//@Order(Ordered.LOWEST_PRECEDENCE - 50000)
public class Application {

    @Configuration
    @ComponentScan
    static class MvcConfiguration extends WebMvcConfigurerAdapter {
        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            registry.addViewController("/").setViewName("upload");
        }
    }

    @Configuration
    @Order(200)//Workaround to fix up the @Order 100 has been taken.
    static class SecurityConfiguration extends WebSecurityConfigurerAdapter {
        protected void configure(HttpSecurity http) throws Exception {
            http
                    .authorizeRequests()
                    .antMatchers("/approve").hasAuthority("photoReviewers")
                    .antMatchers("/").authenticated()
                    .and()
                    .csrf().disable()
                    .httpBasic();
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
    IntegrationFlow inboundProcess(
            ActivitiInboundGateway activitiInboundGateway, PhotoService photoService) {
        return IntegrationFlows
                .from(activitiInboundGateway)
                .handle(
                        new GenericHandler<ActivityExecution>() {
                            @Override
                            public Object handle(ActivityExecution execution, Map<String, Object> headers) {

                                Photo photo = (Photo) execution.getVariable("photo");
                                Long photoId = photo.getId();
                                System.out.println("integration: handling execution " + headers.toString());
                                System.out.println("integration: handling photo #" + photoId);

                                photoService.dogifyPhoto(photo);

                                return MessageBuilder.withPayload(execution)
                                        .setHeader("processed", (Object) true)
                                        .copyHeaders(headers).build();
                            }
                        }
                )
                .get();
    }

    @Bean
    DogePhotoManipulator dogePhotoManipulator() {
        DogePhotoManipulator dogePhotoManipulator = new DogePhotoManipulator();
        dogePhotoManipulator.addTextOverlay("pivotal", "abstractfactorybean", "java");
        dogePhotoManipulator.addTextOverlay("spring", "annotations", "boot");
        dogePhotoManipulator.addTextOverlay("code", "semicolonfree", "groovy");
        dogePhotoManipulator.addTextOverlay("clean", "juergenized", "spring");
        dogePhotoManipulator.addTextOverlay("workflow", "activiti", "BPM");
        return dogePhotoManipulator;
    }

    @Bean
    CommandLineRunner init(IdentityService identityService) {
        return new CommandLineRunner() {
            @Override
            public void run(String... strings) throws Exception {

                // install groups & users
                Group approvers = group("photoReviewers");
                Group uploaders = group("photoUploaders");

                User joram = user("jbarrez", "Joram", "Barrez");
                identityService.createMembership(joram.getId(), approvers.getId());
                identityService.createMembership(joram.getId(), uploaders.getId());

                User josh = user("jlong", "Josh", "Long");
                identityService.createMembership(josh.getId(), uploaders.getId());
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
            return new FileInputStream(new File(this.uploadDirectory.getFile(), Long.toString(photo.getId())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Photo createPhoto(String userId, InputStream bytesForImage) {
        Photo photo = this.photoRepository.save(new Photo((userId), false));
        writePhoto(photo, bytesForImage);
        return photo;
    }

    public ProcessInstance launchPhotoProcess(List<Photo> photos) {
        return runtimeService.startProcessInstanceByKey("photoProcess", Collections.singletonMap("photos", photos));
    }

    public void dogifyPhoto(Photo photo) {
        try {
            InputStream inputStream = this.photoManipulator.manipulate(() -> this.readPhoto(photo)).getInputStream();
            writePhoto(photo, inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private PhotoManipulator photoManipulator;
}


@Controller
class PhotoMvcController {

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private TaskService taskService;


    @RequestMapping(method = RequestMethod.POST, value = "/upload")
    String upload(MultipartHttpServletRequest request, Principal principal) {

        System.out.println("uploading for " + principal.toString());
        Optional.ofNullable(request.getMultiFileMap()).ifPresent(m -> {

            // gather all the MFs in one collection
            List<MultipartFile> multipartFiles = new ArrayList<>();
            m.values().forEach((t) -> {
                multipartFiles.addAll(t);
            });

            // convert them all into `Photo`s
            List<Photo> photos = multipartFiles.stream().map(f -> {
                try {
                    return this.photoService.createPhoto(principal.getName(), f.getInputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

            System.out.println("started photo process w/ process instance ID: " +
                    this.photoService.launchPhotoProcess(photos).getId());

        });
        return "redirect:/";
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
        return "redirect:approve";
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