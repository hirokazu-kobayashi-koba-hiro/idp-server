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

import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Logs the active session mode on application startup.
 *
 * <p>Provides operational visibility into which session management strategy is currently in use
 * (redis, servlet, or disabled). This is particularly useful when troubleshooting session-related
 * issues or verifying environment-specific configurations.
 *
 * <h2>Log Output Example</h2>
 *
 * <pre>
 * Session mode: redis
 * Session mode: servlet
 * Session mode: disabled
 * </pre>
 *
 * <h2>Timing</h2>
 *
 * <p>The log message is emitted after the application context is fully initialized ({@link
 * ApplicationReadyEvent}), ensuring that all configuration properties have been resolved.
 *
 * @see IdpSessionProperties
 * @since 1.0.0
 */
@Component
public class SessionModeLogger implements ApplicationListener<ApplicationReadyEvent> {

  private static final LoggerWrapper logger = LoggerWrapper.getLogger(SessionModeLogger.class);
  private final IdpSessionProperties sessionProperties;

  /**
   * Constructor for SessionModeLogger.
   *
   * @param sessionProperties the session configuration properties
   */
  public SessionModeLogger(IdpSessionProperties sessionProperties) {
    this.sessionProperties = sessionProperties;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    String mode = sessionProperties.getMode().name().toLowerCase();
    logger.info("Session mode: {}", mode);
  }
}
