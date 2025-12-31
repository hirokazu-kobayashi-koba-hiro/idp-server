/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.adapters.springboot;

import java.util.Map;
import org.idp.server.adapters.springboot.application.delegation.PasswordEncoder;
import org.idp.server.adapters.springboot.application.delegation.PasswordVerification;
import org.idp.server.adapters.springboot.application.event.AuditLogPublisherService;
import org.idp.server.adapters.springboot.application.event.SecurityEventPublisherService;
import org.idp.server.adapters.springboot.application.event.UserLifecycleEventPublisherService;
import org.idp.server.adapters.springboot.application.property.AppDatabaseConfigProperties;
import org.idp.server.adapters.springboot.application.property.ControlPlaneDatabaseConfigProperties;
import org.idp.server.adapters.springboot.application.session.AuthSessionCookieService;
import org.idp.server.adapters.springboot.application.session.OAuthSessionService;
import org.idp.server.adapters.springboot.application.session.SessionCookieService;
import org.idp.server.core.adapters.datasource.cache.JedisCacheStore;
import org.idp.server.core.adapters.datasource.config.HikariConnectionProvider;
import org.idp.server.platform.datasource.ApplicationDatabaseTypeProvider;
import org.idp.server.platform.datasource.ConfigurableApplicationDatabaseTypeProvider;
import org.idp.server.platform.datasource.DatabaseConfig;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.datasource.DbConfig;
import org.idp.server.platform.datasource.cache.CacheConfiguration;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.date.TimeConfig;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@EnableAsync
@EnableScheduling
@Configuration
@EnableConfigurationProperties(AppDatabaseConfigProperties.class)
public class IdPServerConfiguration {

  @Value("${idp.configurations.adminTenantId}")
  String adminTenantId;

  @Value("${idp.configurations.encryptionKey}")
  String encryptionKey;

  @Value("${idp.configurations.databaseType}")
  String databaseType;

  @Value("${idp.cache.enabled}")
  boolean enabledCache;

  @Value("${idp.cache.timeToLiveSecond}")
  int timeToLiveSecond;

  @Value("${idp.cache.redis.host}")
  String redisHost;

  @Value("${idp.cache.redis.port}")
  int redisPort;

  @Value("${idp.cache.redis.database}")
  int redisDatabase;

  @Value("${idp.cache.redis.timeout}")
  int redisTimeout;

  @Value("${idp.cache.redis.password}")
  String redisPassword;

  @Value("${idp.cache.redis.maxTotal}")
  int maxTotal;

  @Value("${idp.cache.redis.maxIdle}")
  int maxIdle;

  @Value("${idp.cache.redis.minIdle}")
  int minIdle;

  @Value("${idp.time.zone}")
  String timeZone;

  @Autowired ControlPlaneDatabaseConfigProperties controlPlaneDatabaseConfigProperties;
  @Autowired AppDatabaseConfigProperties appDatabaseConfigProperties;

  @Bean
  public IdpServerApplication idpServerApplication(
      OAuthSessionService oAuthSessionService,
      SessionCookieService sessionCookieService,
      AuthSessionCookieService authSessionCookieService,
      SecurityEventPublisherService eventPublisherService,
      AuditLogPublisherService auditLogPublisher,
      UserLifecycleEventPublisherService userLifecycleEventPublisherService) {

    ApplicationDatabaseTypeProvider applicationDatabaseTypeProvider =
        new ConfigurableApplicationDatabaseTypeProvider(databaseType);

    HikariConnectionProvider dbConnectionProvider = createHikariConnectionProvider();

    CacheStore cacheStore = createCacheStore();

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);
    TimeConfig timeConfig = new TimeConfig(timeZone);

    return new IdpServerApplication(
        adminTenantId,
        applicationDatabaseTypeProvider,
        dbConnectionProvider,
        encryptionKey,
        databaseType,
        cacheStore,
        oAuthSessionService,
        sessionCookieService,
        authSessionCookieService,
        passwordEncoder,
        passwordVerification,
        eventPublisherService,
        auditLogPublisher,
        userLifecycleEventPublisherService,
        timeConfig);
  }

  private HikariConnectionProvider createHikariConnectionProvider() {
    DatabaseType currentDatabaseType = DatabaseType.of(databaseType);

    DbConfig controlPlaneWriterConfig =
        controlPlaneDatabaseConfigProperties.getWriter().toDbConfig();
    DbConfig controlPlaneReaderConfig =
        controlPlaneDatabaseConfigProperties.getReader().toDbConfig();

    Map<DatabaseType, DbConfig> controlPlaneWriterConfigs =
        Map.of(currentDatabaseType, controlPlaneWriterConfig);
    Map<DatabaseType, DbConfig> controlPlaneReaderConfigs =
        Map.of(currentDatabaseType, controlPlaneReaderConfig);

    DatabaseConfig controlPlaneDatabaseConfig =
        new DatabaseConfig(controlPlaneWriterConfigs, controlPlaneReaderConfigs);

    DbConfig appWriterConfig = appDatabaseConfigProperties.getWriter().toDbConfig();
    DbConfig appReaderConfig = appDatabaseConfigProperties.getReader().toDbConfig();

    Map<DatabaseType, DbConfig> appWriterConfigs = Map.of(currentDatabaseType, appWriterConfig);
    Map<DatabaseType, DbConfig> appReaderConfigs = Map.of(currentDatabaseType, appReaderConfig);

    DatabaseConfig appDatabaseConfig = new DatabaseConfig(appWriterConfigs, appReaderConfigs);
    return new HikariConnectionProvider(controlPlaneDatabaseConfig, appDatabaseConfig);
  }

  private CacheStore createCacheStore() {
    if (enabledCache) {
      CacheConfiguration cacheConfiguration =
          new CacheConfiguration(
              redisHost,
              redisPort,
              redisDatabase,
              redisTimeout,
              redisPassword,
              maxTotal,
              maxIdle,
              minIdle,
              timeToLiveSecond);
      return new JedisCacheStore(cacheConfiguration);
    }

    return new NoOperationCacheStore();
  }
}
