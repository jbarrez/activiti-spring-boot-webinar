package demo;

import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

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
            FileInputStream fileInputStream =
                    new FileInputStream(new File(String.format("/Users/jlong/Desktop/%s", fn)));
            return fileInputStream;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
      CommandLineRunner photoProcess(PhotoService photoService, RuntimeService runtimeService, TaskService taskService) {
        return args -> {

            Long userId = 242L;
            List<Photo> photos = Arrays.asList("2.jpg", "1.jpg").stream()
                    .map(pn -> photoService.createPhoto(userId, bytes(pn)))
                    .collect(Collectors.toList());

            ProcessInstance processInstance = photoService.launchPhotoProcess(photos);
            System.out.println("launched process " + processInstance.getProcessDefinitionId() + '.');

            photos.forEach(p -> {
                Execution execution = runtimeService.createExecutionQuery()
                        .processInstanceId(processInstance.getId())
                        .activityId("wait")
                        .variableValueEquals(p).singleResult();
                runtimeService.signal(execution.getId());
            });

            Task task = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .taskCandidateGroup("photoReviewers").singleResult();
            System.out.println("Task name = " + task.getName());

            ((List<Photo>)taskService.getVariable(task.getId(), "photos")).forEach( p -> System.out.println( "Reviewing photo#" + p.getId() + ". "));

            taskService.complete(task.getId(), Collections.singletonMap("approved", true));


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

    @PostConstruct
    public void beforeService() throws Exception {
        File where = this.uploadDirectory.getFile();
        Assert.isTrue(where.exists() || where.mkdirs(), "the " + where.getAbsolutePath() + " folder must exist!");
    }

    @Value("file://#{ systemProperties['user.home']}/Desktop/uploads")
    private Resource uploadDirectory;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private PhotoRepository photoRepository;

    private String photoProcessKey = "photoProcess";

    private File forPhoto(Photo photo) throws IOException {
        return new File(this.uploadDirectory.getFile(), Long.toString(photo.getId()));
    }

    private InputStream readPhoto(Photo photo) throws IOException {
        return new FileInputStream(this.forPhoto(photo));
    }

    private void writePhoto(Photo photo, InputStream inputStream) {
        try {
            try (InputStream input = inputStream; FileOutputStream output = new FileOutputStream(this.forPhoto(photo))) {
                IOUtils.copy(input, output);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Photo createPhoto(Long userId, InputStream bytesForImage) {
        Photo photo = this.photoRepository.save(new Photo(Long.toString(userId), false));
        writePhoto(photo, bytesForImage);
        return photo;
    }

    /// step 1
    public ProcessInstance launchPhotoProcess(List<Photo> photos) {
        return runtimeService.startProcessInstanceByKey(
                this.photoProcessKey, Collections.singletonMap("photos", photos));
    }

    /// step 2
    public void processSingleUploadedPhoto(Photo photo) {
        System.out.println("processing photo#" + photo.getId());
    }

    /// step 3

}


class PhotoUploadForm {
    private List<MultipartFile> files = new ArrayList<>();

    public List<MultipartFile> getFiles() {
        return files;
    }

    public void setFiles(List<MultipartFile> files) {
        this.files = files;
    }
}

@RestController
class UploadController {

    private final RuntimeService runtimeService;


    @Autowired
    public UploadController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @RequestMapping(method = RequestMethod.POST, value = "/up")
    public void upload(MultipartHttpServletRequest request) {
        Optional.ofNullable(request.getMultiFileMap()).ifPresent(m -> m.forEach((k, v) -> System.out.println(k + '=' + v.toString())));
    }


}