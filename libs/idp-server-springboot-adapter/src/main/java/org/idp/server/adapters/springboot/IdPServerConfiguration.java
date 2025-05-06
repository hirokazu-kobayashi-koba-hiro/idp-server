package org.idp.server.adapters.springboot;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.event.SecurityEventPublisherService;
import org.idp.server.adapters.springboot.event.UserLifecycleEventPublisherService;
import org.idp.server.basic.datasource.DatabaseConfig;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.DbConfig;
import org.idp.server.core.adapters.datasource.config.HikariConnectionProvider;
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
      SecurityEventPublisherService eventPublisherService,
      UserLifecycleEventPublisherService userLifecycleEventPublisherService) {

    Map<DatabaseType, DbConfig> writerConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            DbConfig.defaultConfig(postgresqlUrl, postgresqlUsername, postgresqlPassword),
            DatabaseType.MYSQL,
            DbConfig.defaultConfig(mysqlUrl, mysqlUsername, mysqlPassword));

    Map<DatabaseType, DbConfig> readerConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            DbConfig.defaultConfig(postgresqlUrl, postgresqlUsername, postgresqlPassword),
            DatabaseType.MYSQL,
            DbConfig.defaultConfig(mysqlUrl, mysqlUsername, mysqlPassword));

    DatabaseConfig databaseConfig = new DatabaseConfig(writerConfigs, readerConfigs);
    HikariConnectionProvider dbConnectionProvider = new HikariConnectionProvider(databaseConfig);

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);

    return new IdpServerApplication(
        adminTenantId,
        dbConnectionProvider,
        encryptionKey,
        oAuthSessionService,
        passwordEncoder,
        passwordVerification,
        eventPublisherService,
        userLifecycleEventPublisherService);
  }
}
