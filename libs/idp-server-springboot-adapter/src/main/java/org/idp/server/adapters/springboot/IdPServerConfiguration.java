package org.idp.server.adapters.springboot;

import java.util.Map;
import org.idp.server.adapters.springboot.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.event.SecurityEventPublisherService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.basic.sql.DatabaseConfig;
import org.idp.server.core.basic.sql.DbCredentials;
import org.idp.server.core.basic.sql.Dialect;
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
      OAuthSessionService oAuthSessionService,
      SecurityEventPublisherService eventPublisherService) {

    Map<Dialect, DbCredentials> writerConfigs =
        Map.of(
            Dialect.POSTGRESQL, new DbCredentials(databaseUrl, databaseUsername, databasePassword));

    Map<Dialect, DbCredentials> readerConfigs =
        Map.of(
            Dialect.POSTGRESQL, new DbCredentials(databaseUrl, databaseUsername, databasePassword));

    DatabaseConfig databaseConfig = new DatabaseConfig(writerConfigs, readerConfigs);

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
