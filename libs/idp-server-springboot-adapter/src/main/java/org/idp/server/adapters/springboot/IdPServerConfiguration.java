package org.idp.server.adapters.springboot;

import org.idp.server.adapters.springboot.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.event.SecurityEventPublisherService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.handler.config.DatabaseConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@EnableAsync
@EnableScheduling
@Configuration
public class IdPServerConfiguration {

  @Value("${spring.datasource.url}")
  String databaseUrl;

  @Value("${spring.datasource.username}")
  String databaseUsername;

  @Value("${spring.datasource.password}")
  String databasePassword;

  @Value("${idp.configurations.encryptionKey}")
  String encryptionKey;

  @Bean
  public IdpServerApplication idpServerApplication(
      OAuthSessionService oAuthSessionService, SecurityEventPublisherService eventPublisherService) {

    DatabaseConfig databaseConfig =
        new DatabaseConfig(databaseUrl, databaseUsername, databasePassword);

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);

    return new IdpServerApplication(
        databaseConfig,
        encryptionKey,
        oAuthSessionService,
        passwordEncoder,
        passwordVerification,
        eventPublisherService);
  }
}
