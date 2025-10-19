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

package org.idp.server.adapters.springboot.application.restapi;

import org.springframework.http.HttpHeaders;

/**
 * Interface for configuring security headers in HTTP responses.
 *
 * <p>This interface provides default methods to create security headers that protect against common
 * web vulnerabilities. Implementing classes can use these methods to ensure consistent security
 * header configuration across all API endpoints.
 *
 * <p><b>Security Headers:</b>
 *
 * <ul>
 *   <li><b>X-Content-Type-Options: nosniff</b> - Prevents MIME type sniffing attacks by ensuring
 *       browsers respect the declared Content-Type
 *   <li><b>Strict-Transport-Security</b> - Enforces HTTPS connections for 1 year including
 *       subdomains (only effective over HTTPS; ignored over HTTP)
 * </ul>
 *
 * <p><b>Industry Standard Compliance:</b>
 *
 * <ul>
 *   <li>Google OAuth: X-Content-Type-Options configured
 *   <li>Microsoft Azure AD: Both headers configured
 *   <li>Keycloak: Both headers configured at application level
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>{@code
 * @RestController
 * public class TokenV1Api implements SecurityHeaderConfigurable {
 *
 *   @PostMapping
 *   public ResponseEntity<?> request(...) {
 *     TokenRequestResponse response = tokenApi.request(...);
 *
 *     HttpHeaders headers = createSecurityHeaders();
 *     headers.setCacheControl("no-store, no-cache, must-revalidate, private");
 *     headers.setContentType(MediaType.APPLICATION_JSON);
 *
 *     return new ResponseEntity<>(response.contents(), headers, HttpStatus.OK);
 *   }
 * }
 * }</pre>
 *
 * @see org.idp.server.adapters.springboot.application.restapi.metadata.OpenIdDiscoveryV1Api
 * @see org.idp.server.adapters.springboot.application.restapi.token.TokenV1Api
 * @see org.idp.server.adapters.springboot.application.restapi.userinfo.UserinfoV1Api
 */
public interface SecurityHeaderConfigurable {

  /**
   * Creates HTTP headers with default security configurations.
   *
   * <p>This method provides a base set of security headers that should be included in all HTTP
   * responses. The headers are configured according to industry best practices and OAuth/OIDC
   * provider standards.
   *
   * <p><b>Headers Configured:</b>
   *
   * <ul>
   *   <li><b>X-Content-Type-Options: nosniff</b>
   *       <ul>
   *         <li>Prevents MIME type sniffing attacks
   *         <li>Ensures browsers always use the declared Content-Type
   *         <li>Effective for both HTML and JSON responses
   *       </ul>
   *   <li><b>Strict-Transport-Security: max-age=31536000; includeSubDomains</b>
   *       <ul>
   *         <li>Enforces HTTPS for 1 year (31536000 seconds)
   *         <li>Applies to all subdomains
   *         <li>Only effective when served over HTTPS (ignored over HTTP)
   *         <li>Safe for local development (HTTP connections ignore this header)
   *       </ul>
   * </ul>
   *
   * <p><b>Additional Headers:</b> Implementing classes should add endpoint-specific headers:
   *
   * <ul>
   *   <li><b>Cache-Control</b> - Endpoint-specific caching policy
   *   <li><b>Content-Type</b> - Response content type (typically application/json)
   * </ul>
   *
   * <p><b>Implementation Note:</b> The Strict-Transport-Security header is safe to include even in
   * local HTTP development environments because browsers ignore HSTS headers received over HTTP
   * connections. It will automatically become effective when deployed in production HTTPS
   * environments.
   *
   * @return HttpHeaders with security headers configured
   */
  default HttpHeaders createSecurityHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    return headers;
  }
}
