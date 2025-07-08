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
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.delegation.PasswordEncoder;
import org.idp.server.adapters.springboot.application.delegation.PasswordVerification;
import org.idp.server.adapters.springboot.application.event.SecurityEventPublisherService;
import org.idp.server.adapters.springboot.application.event.UserLifecycleEventPublisherService;
import org.idp.server.adapters.springboot.application.property.AdminDatabaseConfigProperties;
import org.idp.server.adapters.springboot.application.property.AppDatabaseConfigProperties;
import org.idp.server.adapters.springboot.application.session.OAuthSessionService;
import org.idp.server.control_plane.base.AdminDashboardUrl;
import org.idp.server.core.adapters.datasource.cache.JedisCacheStore;
import org.idp.server.core.adapters.datasource.cache.NoOperationCacheStore;
import org.idp.server.core.adapters.datasource.config.HikariConnectionProvider;
import org.idp.server.platform.datasource.DatabaseConfig;
import org.idp.server.platform.datasource.DatabaseType;
import org.idp.server.platform.datasource.DbConfig;
import org.idp.server.platform.datasource.cache.CacheConfiguration;
import org.idp.server.platform.datasource.cache.CacheStore;
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

  @Value("${idp.configurations.adminDashboardUrl}")
  String adminDashboardUrl;

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

  @Autowired AdminDatabaseConfigProperties adminDatabaseConfigProperties;
  @Autowired AppDatabaseConfigProperties appDatabaseConfigProperties;

  @Bean
  public IdpServerApplication idpServerApplication(
      OAuthSessionService oAuthSessionService,
      SecurityEventPublisherService eventPublisherService,
      UserLifecycleEventPublisherService userLifecycleEventPublisherService) {

    HikariConnectionProvider dbConnectionProvider = createHikariConnectionProvider();

    CacheStore cacheStore = createCacheStore();

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);
    AdminDashboardUrl adminDashboardUrl1 = new AdminDashboardUrl(adminDashboardUrl);

    return new IdpServerApplication(
        adminTenantId,
        adminDashboardUrl1,
        dbConnectionProvider,
        encryptionKey,
        cacheStore,
        oAuthSessionService,
        passwordEncoder,
        passwordVerification,
        eventPublisherService,
        userLifecycleEventPublisherService);
  }

  private HikariConnectionProvider createHikariConnectionProvider() {
    Map<DatabaseType, DbConfig> adminWriterConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            adminDatabaseConfigProperties.getPostgresql().get("writer").toDbConfig(),
            DatabaseType.MYSQL,
            adminDatabaseConfigProperties.getMysql().get("writer").toDbConfig());

    Map<DatabaseType, DbConfig> adminReaderConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            adminDatabaseConfigProperties.getPostgresql().get("reader").toDbConfig(),
            DatabaseType.MYSQL,
            adminDatabaseConfigProperties.getMysql().get("reader").toDbConfig());

    DatabaseConfig adminDatabaseConfig = new DatabaseConfig(adminWriterConfigs, adminReaderConfigs);

    Map<DatabaseType, DbConfig> appWriterConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            appDatabaseConfigProperties.getPostgresql().get("writer").toDbConfig(),
            DatabaseType.MYSQL,
            appDatabaseConfigProperties.getMysql().get("writer").toDbConfig());

    Map<DatabaseType, DbConfig> appReaderConfigs =
        Map.of(
            DatabaseType.POSTGRESQL,
            appDatabaseConfigProperties.getPostgresql().get("reader").toDbConfig(),
            DatabaseType.MYSQL,
            appDatabaseConfigProperties.getMysql().get("reader").toDbConfig());

    DatabaseConfig appDatabaseConfig = new DatabaseConfig(appWriterConfigs, appReaderConfigs);
    return new HikariConnectionProvider(adminDatabaseConfig, appDatabaseConfig);
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
