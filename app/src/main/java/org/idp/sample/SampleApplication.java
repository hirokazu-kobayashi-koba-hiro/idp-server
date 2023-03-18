package org.idp.sample;

import org.idp.server.IdpServerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @Bean
    public IdpServerApplication idpServerApplication() {
        List<String> paths = new ArrayList<>();
        return IdpServerApplication.initializeWithInMemory(paths, paths);
    }

}
