package demo;

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
	
	 

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@Component 
class PhotoService {
	
	public void processPhoto( long photoId) {
		System.out.println( "about to porocess photo # " + photoId);
	}
}