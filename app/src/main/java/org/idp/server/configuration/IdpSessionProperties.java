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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for session management modes in idp-server.
 *
 * <p>Enables switching between different session storage mechanisms via environment variables,
 * supporting flexible deployment scenarios ranging from stateless APIs to clustered production
 * environments.
 *
 * <h2>Supported Modes</h2>
 *
 * <ul>
 *   <li><b>redis</b> — Uses Spring Session with Redis backend via {@code
 *       SafeRedisSessionRepository}. Recommended for production and multi-instance deployments.
 *   <li><b>servlet</b> — Uses standard servlet {@code HttpSession} (in-memory, single instance).
 *       Suitable for local development and lightweight environments.
 *   <li><b>disabled</b> — Disables session mechanisms entirely. For stateless REST/JWT-based APIs.
 * </ul>
 *
 * <h2>Configuration Example</h2>
 *
 * <pre>{@code
 * # application.yaml
 * idp:
 *   session:
 *     mode: ${IDP_SESSION_MODE:servlet}
 * }</pre>
 *
 * <h2>Environment Variable Usage</h2>
 *
 * <pre>{@code
 * # Redis mode (production)
 * IDP_SESSION_MODE=redis REDIS_HOST=redis java -jar idp-server.jar
 *
 * # Servlet mode (local development)
 * IDP_SESSION_MODE=servlet java -jar idp-server.jar
 *
 * # Disabled mode (stateless API)
 * IDP_SESSION_MODE=disabled java -jar idp-server.jar
 * }</pre>
 *
 * @see org.idp.server.configuration.IdpRedisSessionAutoConfiguration
 * @see org.idp.server.SafeRedisSessionRepository
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "idp.session")
public class IdpSessionProperties {

  /** Session storage mode: redis, servlet, or disabled. Defaults to servlet. */
  private SessionMode mode = SessionMode.SERVLET;

  /**
   * Gets the current session mode.
   *
   * @return the configured session mode
   */
  public SessionMode getMode() {
    return mode;
  }

  /**
   * Sets the session mode.
   *
   * @param mode the session mode to use
   */
  public void setMode(SessionMode mode) {
    this.mode = mode;
  }

  /** Enumeration of supported session modes. */
  public enum SessionMode {
    /** Redis-backed session with fail-safe handling. */
    REDIS,
    /** Standard servlet HttpSession (in-memory). */
    SERVLET,
    /** Session mechanisms disabled (stateless). */
    DISABLED
  }
}
