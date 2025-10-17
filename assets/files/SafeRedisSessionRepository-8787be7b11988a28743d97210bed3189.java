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

package org.idp.server;

import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

/**
 * A safer extension of {@link org.springframework.session.data.redis.RedisIndexedSessionRepository}
 * that tolerates transient Redis failures during session operations.
 *
 * <h2>Overview</h2>
 *
 * <p>{@code SafeRedisSessionRepository} provides a fail-safe wrapper around the default {@link
 * RedisIndexedSessionRepository}, designed for environments where Redis may be temporarily
 * unavailable or network partitions can occur. Instead of propagating {@link
 * org.springframework.data.redis.RedisConnectionFailureException} or other runtime exceptions to
 * the application layer, this implementation logs error and gracefully degrades by returning {@code
 * null} or ignoring write operations.
 *
 * <h2>Behavior Differences</h2>
 *
 * <ul>
 *   <li><b>save(Session)</b> — Logs a error if Redis write fails, rather than throwing an
 *       exception.
 *   <li><b>findById(String)</b> — Returns {@code null} if Redis read fails, instead of propagating
 *       an error.
 *   <li><b>deleteById(String)</b> — Logs a error and continues execution if deletion fails.
 * </ul>
 *
 * <p>This allows HTTP requests to continue (e.g., with stateless JWT or fallback session logic)
 * even when the Redis backend is temporarily unreachable. However, note that session consistency
 * and persistence are not guaranteed during such failures.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>High-availability identity providers where session loss is acceptable during Redis
 *       downtime.
 *   <li>Graceful degradation strategy for distributed authentication systems (e.g., {@code
 *       idp-server}).
 *   <li>Hybrid session management where Redis is optional or used primarily for shared session
 *       cache.
 * </ul>
 *
 * <h2>Logging</h2>
 *
 * <p>All failure events are logged via {@link org.idp.server.platform.log.LoggerWrapper} at ERROR
 * level, using concise contextual messages.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * @Bean
 * public SafeRedisSessionRepository sessionRepository(RedisOperations<String, Object> ops) {
 *     return new SafeRedisSessionRepository(ops);
 * }
 * }</pre>
 *
 * @see org.springframework.session.data.redis.RedisIndexedSessionRepository
 * @see org.springframework.session.web.http.SessionRepositoryFilter
 * @author Hiro...
 * @since 0.9.0
 */
public class SafeRedisSessionRepository extends RedisIndexedSessionRepository {

  // Logger for this class
  private static final LoggerWrapper logger =
      LoggerWrapper.getLogger(SafeRedisSessionRepository.class);

  /**
   * Constructor for SafeRedisSessionRepository.
   *
   * @param sessionRedisOperations Redis operations for session management.
   */
  public SafeRedisSessionRepository(RedisOperations<String, Object> sessionRedisOperations) {
    super(sessionRedisOperations);
  }

  /**
   * Save the session to Redis. If an exception occurs (e.g., Redis is disconnected), log a warning
   * and suppress the exception.
   */
  @Override
  public void save(RedisSession session) {
    try {
      super.save(session);
    } catch (Exception e) {
      logger.error("Failed to save session (Redis disconnected): {}", e.getMessage());
    }
  }

  /** Find a session by its ID. If an exception occurs, log a warning and return null. */
  @Override
  public RedisSession findById(String id) {
    try {
      return super.findById(id);
    } catch (Exception e) {
      logger.error("Failed to load session: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Delete a session by its ID. If an exception occurs, log a warning and suppress the exception.
   */
  @Override
  public void deleteById(String id) {
    try {
      super.deleteById(id);
    } catch (Exception e) {
      logger.error("Failed to delete session: {}", e.getMessage());
    }
  }
}
