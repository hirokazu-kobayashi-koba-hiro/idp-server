package org.idp.sample;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.idp.server.IdpServerApplication;
import org.idp.server.handler.config.DatabaseConfig;
import org.idp.server.handler.config.MemoryDataSourceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class IdPApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdPApplication.class, args);
  }

  @Value("${idp.configurations.basePath}")
  String configurationBasePath;

  @Value("${spring.datasource.url}")
  String databaseUrl;

  @Bean
  public IdpServerApplication idpServerApplication() {
    List<String> serverPaths = new ArrayList<>();
    serverPaths.add(configurationBasePath + "/server.json");
    serverPaths.add(configurationBasePath + "/unsupportedServer.json");

    List<String> clientPaths = new ArrayList<>();
    clientPaths.add(configurationBasePath + "/clients/clientSecretBasic.json");
    clientPaths.add(configurationBasePath + "/clients/clientSecretBasic2.json");
    clientPaths.add(configurationBasePath + "/clients/clientSecretPost.json");
    clientPaths.add(configurationBasePath + "/clients/clientSecretPostWithIdTokenEnc.json");
    clientPaths.add(configurationBasePath + "/clients/clientSecretJwt.json");
    clientPaths.add(configurationBasePath + "/clients/privateKeyJwt.json");
    clientPaths.add(configurationBasePath + "/clients/publicClient.json");
    clientPaths.add(configurationBasePath + "/clients/selfSignedTlsClientAuth.json");
    clientPaths.add(configurationBasePath + "/clients/unsupportedClient.json");
    clientPaths.add(configurationBasePath + "/clients/unsupportedServerUnsupportedClient.json");

    MemoryDataSourceConfig memoryDataSourceConfig =
        new MemoryDataSourceConfig(serverPaths, clientPaths);
    DatabaseConfig databaseConfig =
        new DatabaseConfig("jdbc:postgresql://localhost:5432/idpserver", "idpserver", "idpserver");
    return new IdpServerApplication(databaseConfig);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

}
