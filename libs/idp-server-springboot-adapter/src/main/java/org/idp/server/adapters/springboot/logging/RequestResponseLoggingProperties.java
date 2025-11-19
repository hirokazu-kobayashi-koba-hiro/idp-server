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

package org.idp.server.adapters.springboot.logging;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for HTTP request/response debug logging.
 *
 * <p>Enables detailed logging of HTTP requests and responses for OAuth/OIDC endpoints, useful for
 * integration testing and debugging parameter mismatches with external services.
 *
 * <h2>Configuration Example (application.properties)</h2>
 *
 * <pre>
 * # Enable request/response logging
 * idp.logging.request-response.enabled=true
 *
 * # Mask sensitive tokens (default: true)
 * idp.logging.request-response.mask-tokens=true
 *
 * # Target endpoints (empty = all endpoints)
 * idp.logging.request-response.endpoints=/v1/token,/v1/authorizations
 *
 * # Maximum body size to log (bytes)
 * idp.logging.request-response.max-body-size=10000
 * </pre>
 *
 * <h2>Security Considerations</h2>
 *
 * <ul>
 *   <li><b>Default disabled</b>: Prevents accidental logging in production
 *   <li><b>Token masking</b>: Automatically masks access_token, refresh_token, id_token, etc.
 *   <li><b>Multi-layer protection</b>: Property + Log Level + Masking
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "idp.logging.request-response")
public class RequestResponseLoggingProperties {

  /** Enable/disable request-response logging. Default: false (production-safe) */
  private boolean enabled = false;

  /** Enable token masking for security. Default: true */
  private boolean maskTokens = true;

  /**
   * Target endpoints to log. Empty list means all endpoints.
   *
   * <p>Example: ["/v1/tokens", "/v1/authorizations", "/v1/backchannel/authentications",
   * "/v1/userinfo"]
   */
  private List<String> endpoints =
      List.of(
          "/v1/tokens", "/v1/authorizations", "/v1/backchannel/authentications", "/v1/userinfo");

  /** Maximum body size to log in bytes. Default: 10000 */
  private int maxBodySize = 10000;

  /**
   * Parameters to mask in logs.
   *
   * <p>Default: access_token, refresh_token, id_token, client_secret, password
   */
  private List<String> maskParameters =
      List.of("access_token", "refresh_token", "id_token", "client_secret", "password");

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isMaskTokens() {
    return maskTokens;
  }

  public void setMaskTokens(boolean maskTokens) {
    this.maskTokens = maskTokens;
  }

  public List<String> getEndpoints() {
    return endpoints;
  }

  public void setEndpoints(List<String> endpoints) {
    this.endpoints = endpoints;
  }

  public int getMaxBodySize() {
    return maxBodySize;
  }

  public void setMaxBodySize(int maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  public List<String> getMaskParameters() {
    return maskParameters;
  }

  public void setMaskParameters(List<String> maskParameters) {
    this.maskParameters = maskParameters;
  }
}
