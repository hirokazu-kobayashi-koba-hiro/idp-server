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

package org.idp.server.configuration;

import org.idp.server.SafeRedisSessionRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Auto-configuration for Redis-backed session management in idp-server.
 *
 * <p>This configuration is activated only when all of the following conditions are met:
 *
 * <ul>
 *   <li>{@code spring-session-data-redis} is on the classpath
 *   <li>{@code idp.session.mode} is set to {@code redis}
 *   <li>No user-defined {@code SessionRepository} bean exists
 * </ul>
 *
 * <h2>Behavior</h2>
 *
 * <p>When activated, this configuration:
 *
 * <ul>
 *   <li>Registers {@link SafeRedisSessionRepository} for fail-safe Redis session storage
 *   <li>Configures {@link SessionRepositoryFilter} for HTTP session integration
 *   <li>Logs the session mode on startup for operational visibility
 * </ul>
 *
 * <h2>Configuration Example</h2>
 *
 * <pre>{@code
 * # application.yaml
 * idp:
 *   session:
 *     mode: redis  # Activates this auto-configuration
 *
 * spring:
 *   data:
 *     redis:
 *       host: ${REDIS_HOST:localhost}
 *       port: ${REDIS_PORT:6379}
 * }</pre>
 *
 * <h2>Graceful Degradation</h2>
 *
 * <p>The {@link SafeRedisSessionRepository} implementation tolerates transient Redis failures,
 * logging errors instead of propagating exceptions. This allows the application to continue serving
 * requests (potentially with degraded session functionality) during Redis outages.
 *
 * <h2>Alternative Modes</h2>
 *
 * <ul>
 *   <li><b>servlet</b> — Standard servlet HttpSession (no auto-configuration needed)
 *   <li><b>disabled</b> — No session management (no auto-configuration needed)
 * </ul>
 *
 * @see IdpSessionProperties
 * @see SafeRedisSessionRepository
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(RedisIndexedSessionRepository.class)
@ConditionalOnProperty(prefix = "idp.session", name = "mode", havingValue = "redis")
@EnableConfigurationProperties(IdpSessionProperties.class)
public class IdpRedisSessionAutoConfiguration {

  private static final LoggerWrapper logger =
      LoggerWrapper.getLogger(IdpRedisSessionAutoConfiguration.class);

  /**
   * Creates a fail-safe Redis session repository.
   *
   * <p>This bean is registered only if no user-defined session repository exists.
   *
   * @param connectionFactory the Redis connection factory
   * @return a configured SafeRedisSessionRepository instance
   */
  @Bean
  @ConditionalOnMissingBean
  public SafeRedisSessionRepository sessionRepository(RedisConnectionFactory connectionFactory) {
    logger.info("Session mode: redis");

    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new JdkSerializationRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new JdkSerializationRedisSerializer());
    template.afterPropertiesSet();

    return new SafeRedisSessionRepository(template);
  }

  /**
   * Creates the session repository filter for HTTP integration.
   *
   * <p>This filter intercepts HTTP requests to manage session lifecycle using the configured {@link
   * SafeRedisSessionRepository}.
   *
   * @param sessionRepository the session repository to use
   * @return a configured SessionRepositoryFilter instance
   */
  @Bean
  @ConditionalOnMissingBean
  public SessionRepositoryFilter sessionRepositoryFilter(
      SafeRedisSessionRepository sessionRepository) {
    return new SessionRepositoryFilter<>(sessionRepository);
  }
}
