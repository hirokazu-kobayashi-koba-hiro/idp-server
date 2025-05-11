package org.idp.server.adapters.springboot;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.delegation.PasswordEncoder;
import org.idp.server.adapters.springboot.application.delegation.PasswordVerification;
import org.idp.server.adapters.springboot.application.event.SecurityEventPublisherService;
import org.idp.server.adapters.springboot.application.event.UserLifecycleEventPublisherService;
import org.idp.server.adapters.springboot.application.property.DatabaseConfigProperties;
import org.idp.server.adapters.springboot.application.session.OAuthSessionService;
import org.idp.server.basic.datasource.DatabaseConfig;
import org.idp.server.basic.datasource.DatabaseType;
import org.idp.server.basic.datasource.DbConfig;
import org.idp.server.basic.datasource.cache.CacheConfiguration;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.core.adapters.datasource.cache.JedisCacheStore;
import org.idp.server.core.adapters.datasource.cache.NoOperationCacheStore;
import org.idp.server.core.adapters.datasource.config.HikariConnectionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@EnableAsync
@EnableRedisHttpSession
@EnableScheduling
@Configuration
@EnableConfigurationProperties(DatabaseConfigProperties.class)
public class IdPServerConfiguration {

  @Value("${idp.configurations.adminTenantId}")
  String adminTenantId;

  @Value("${idp.configurations.encryptionKey}")
  String encryptionKey;

  @Value("${idp.cache.enabled}")
  boolean enabledCache;

  @Value("${idp.cache.timeToLiveSecond}")
  int timeToLiveSecond;

  @Value("${idp.cache.redis.host}")
  String redisHost;

  @Value("${idp.cache.redis.port}")
  int redisPort;

  @Value("${idp.cache.redis.maxTotal}")
  int maxTotal;

  @Value("${idp.cache.redis.maxIdle}")
  int maxIdle;

  @Value("${idp.cache.redis.minIdle}")
  int minIdle;

  @Autowired DatabaseConfigProperties databaseConfigProperties;

  @Bean
  public IdpServerApplication idpServerApplication(
      OAuthSessionService oAuthSessionService,
      SecurityEventPublisherService eventPublisherService,
      UserLifecycleEventPublisherService userLifecycleEventPublisherService) {

    Map<DatabaseType, DbConfig> writerConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            databaseConfigProperties.getPostgresql().get("writer").toDbConfig(),
            DatabaseType.MYSQL,
            databaseConfigProperties.getMysql().get("writer").toDbConfig());

    Map<DatabaseType, DbConfig> readerConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            databaseConfigProperties.getPostgresql().get("reader").toDbConfig(),
            DatabaseType.MYSQL,
            databaseConfigProperties.getMysql().get("reader").toDbConfig());

    DatabaseConfig databaseConfig = new DatabaseConfig(writerConfigs, readerConfigs);
    HikariConnectionProvider dbConnectionProvider = new HikariConnectionProvider(databaseConfig);

    CacheStore cacheStore = createCacheStore();

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);

    return new IdpServerApplication(
        adminTenantId,
        dbConnectionProvider,
        encryptionKey,
        cacheStore,
        oAuthSessionService,
        passwordEncoder,
        passwordVerification,
        eventPublisherService,
        userLifecycleEventPublisherService);
  }

  private CacheStore createCacheStore() {
    if (enabledCache) {
      CacheConfiguration cacheConfiguration =
          new CacheConfiguration(
              redisHost, redisPort, maxTotal, maxIdle, minIdle, timeToLiveSecond);
      return new JedisCacheStore(cacheConfiguration);
    }

    return new NoOperationCacheStore();
  }
}
