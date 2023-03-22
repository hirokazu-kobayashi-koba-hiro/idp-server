package org.idp.sample;

import java.util.List;
import org.idp.server.IdpServerApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(SampleApplication.class, args);
  }

  @Value("${idp.configurations.basePath}")
  String configurationBasePath;

  @Bean
  public IdpServerApplication idpServerApplication() {
    List<String> serverPaths = List.of(configurationBasePath + "/server.json");
    List<String> clientPaths = List.of(configurationBasePath + "/clients/clientSecretBasic.json");
    return IdpServerApplication.initializeWithInMemory(serverPaths, clientPaths);
  }
}
