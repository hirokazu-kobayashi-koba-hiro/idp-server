package org.idp.server.adapters.springboot;

import java.util.Map;
import org.idp.server.adapters.springboot.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.event.SecurityEventPublisherService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.basic.sql.DatabaseConfig;
import org.idp.server.core.basic.sql.DatabaseType;
import org.idp.server.core.basic.sql.DbCredentials;
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

  @Value("${idp.configurations.adminTenantId}")
  String adminTenantId;

  @Value("${idp.datasource.postgresql.url}")
  String postgresqlUrl;

  @Value("${idp.datasource.postgresql.username}")
  String postgresqlUsername;

  @Value("${idp.datasource.postgresql.password}")
  String postgresqlPassword;

  @Value("${idp.datasource.mysql.url}")
  String mysqlUrl;

  @Value("${idp.datasource.mysql.username}")
  String mysqlUsername;

  @Value("${idp.datasource.mysql.password}")
  String mysqlPassword;

  @Value("${idp.configurations.encryptionKey}")
  String encryptionKey;

  @Bean
  public IdpServerApplication idpServerApplication(
      OAuthSessionService oAuthSessionService,
      SecurityEventPublisherService eventPublisherService) {

    Map<DatabaseType, DbCredentials> writerConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            new DbCredentials(postgresqlUrl, postgresqlUsername, postgresqlPassword),
            DatabaseType.MYSQL,
            new DbCredentials(mysqlUrl, mysqlUsername, mysqlPassword));

    Map<DatabaseType, DbCredentials> readerConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            new DbCredentials(postgresqlUrl, postgresqlUsername, postgresqlPassword),
            DatabaseType.MYSQL,
            new DbCredentials(mysqlUrl, mysqlUsername, mysqlPassword));

    DatabaseConfig databaseConfig = new DatabaseConfig(writerConfigs, readerConfigs);

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);

    return new IdpServerApplication(
        adminTenantId,
        databaseConfig,
        encryptionKey,
        oAuthSessionService,
        passwordEncoder,
        passwordVerification,
        eventPublisherService);
  }
}
