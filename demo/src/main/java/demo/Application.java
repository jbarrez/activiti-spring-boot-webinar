package demo;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.h2.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.PostConstruct;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

    private final Log logger = LogFactory.getLog(getClass());

/*    @Configuration
    public static class SimpleMvcConfiguration
            extends WebMvcConfigurerAdapter {
        @Override
        public void addViewControllers(ViewControllerRegistry registry) {
            registry.addViewController("/upload.php").setViewName("upload");
        }
    }*/

/*
    @Configuration
    static class ApiWebSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {
        protected void configure(HttpSecurity http) throws Exception {
            http
                    .antMatcher("/api*/

    /**
     * ")
     * .authorizeRequests()
     * .anyRequest().authenticated()
     * .and()
     * .httpBasic();
     * }
     * }
     */
    InputStream bytes(String fn) {
        try {
            return new FileInputStream(new File(String.format(
                    System.getProperty("user.home") + "/Desktop/%s", fn)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    @Bean
    CommandLineRunner photoProcess(PhotoService photoService) {
        return args -> {

            Long userId = 242L;
            List<Photo> photos = Arrays.asList("2.jpg", "1.jpg").stream()
                    .map(pn -> photoService.createPhoto(userId, bytes(pn)))
                    .collect(Collectors.toList());

            ProcessInstance processInstance = photoService.launchPhotoProcess(photos);

            logger.info("launched process " + processInstance.getProcessDefinitionId() + '.');

            photos.forEach(photo -> photoService.completeProcessingPhoto(processInstance.getId(), photo));

            photoService.approvePhotos(processInstance.getId(), true);
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

    private Log logger = LogFactory.getLog(getClass());

    @PostConstruct
    public void beforeService() throws Exception {
        File uploadDir = this.uploadDirectory.getFile();
        Assert.isTrue(uploadDir.exists() || uploadDir.mkdirs(), "the " + uploadDir.getAbsolutePath() + " folder must exist!");
    }

    @Autowired
    private TaskService taskService;

    @Value("file://#{ systemProperties['user.home'] }/Desktop/uploads")
    private Resource uploadDirectory;

    @Autowired
    private RuntimeService runtimeService;


    @Autowired
    private PhotoRepository photoRepository;

    protected File forPhoto(Photo photo) throws IOException {
        return new File(this.uploadDirectory.getFile(), Long.toString(photo.getId()));
    }

    protected InputStream readPhoto(Photo photo) throws IOException {
        return new FileInputStream(this.forPhoto(photo));
    }

    protected void writePhoto(Photo photo, InputStream inputStream) {
        try {
            try (InputStream input = inputStream; FileOutputStream output = new FileOutputStream(this.forPhoto(photo))) {
                IOUtils.copy(input, output);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /// step 0
    public Photo createPhoto(Long userId, InputStream bytesForImage) {
        Photo photo = this.photoRepository.save(new Photo(Long.toString(userId), false));
        writePhoto(photo, bytesForImage);
        return photo;
    }

    /// step 1
    public ProcessInstance launchPhotoProcess(List<Photo> photos) {
        return runtimeService.startProcessInstanceByKey(
                "photoProcess", Collections.singletonMap("photos", photos));
    }

    /// step 2
    public void startProcessingPhoto(/*String processInstanceId,*/ Photo photo) {
        logger.info("processing photo #" + photo.getId());
    }

    /// step 3
    public void completeProcessingPhoto(String processInstanceId, Photo p) {
        Execution execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceId)
                .activityId("wait")
                .variableValueEquals(p)
                .singleResult();
        runtimeService.signal(execution.getId());
    }

    /// step 4
    public void approvePhotos(String processInstanceId, boolean approve) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskCandidateGroup("photoReviewers")
                .singleResult();

        logger.info("task name = " + task.getName());

        List<Photo> photos = (List<Photo>) taskService.getVariable(task.getId(), "photos");
        photos.forEach(p -> logger.info("reviewing photo #" + p.getId() + ". "));
        taskService.complete(task.getId(), Collections.singletonMap("approved", approve));
    }

}


@RestController
class UploadController {

    @Autowired
    private PhotoService photoService;

    @RequestMapping(method = RequestMethod.POST, value = "/up")
    public void upload(MultipartHttpServletRequest request) {
        Optional.ofNullable(request.getMultiFileMap())
                .ifPresent(m -> m.forEach((k, v) -> System.out.println(k + '=' + v.toString())));
    }


}