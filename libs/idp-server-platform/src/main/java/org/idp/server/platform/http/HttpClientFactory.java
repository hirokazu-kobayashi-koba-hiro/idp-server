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

package org.idp.server.platform.http;

import java.net.http.HttpClient;
import java.time.Duration;

public class HttpClientFactory {

  /**
   * Creates a default HTTP client optimized for external service communication with SSRF
   * protection.
   *
   * <h3>HTTP Version</h3>
   *
   * <p>The client is configured to use HTTP/1.1 for maximum compatibility.
   *
   * <h3>Redirect Behavior (SSRF Protection)</h3>
   *
   * <p>Configured with {@code Redirect.NEVER} policy for SSRF protection:
   *
   * <ul>
   *   <li><strong>Security:</strong> Prevents redirect-based SSRF attacks where an external URL
   *       redirects to internal resources (e.g., 169.254.169.254, localhost)
   *   <li><strong>Manual handling:</strong> Applications must explicitly handle redirects by
   *       checking the Location header and re-validating the redirect URL through SSRF protection
   *   <li><strong>Transparency:</strong> All redirect decisions are visible to application code
   * </ul>
   *
   * <h3>Timeout Configuration</h3>
   *
   * <p>Connection timeout is set to 20 seconds:
   *
   * <ul>
   *   <li><strong>Connect timeout:</strong> Maximum time to establish TCP connection
   *   <li><strong>Request timeout:</strong> Not set globally; should be configured per-request
   *       using {@code HttpRequest.Builder.timeout()}
   * </ul>
   *
   * <h3>Use Cases</h3>
   *
   * <p>This client is designed for:
   *
   * <ul>
   *   <li>External OAuth/OIDC provider communication
   *   <li>Security event transmission (SSF)
   *   <li>Identity verification service integration
   *   <li>Push notification delivery (APNs, FCM)
   *   <li>CIBA client notification callbacks
   * </ul>
   *
   * @return configured HTTP client instance
   * @see java.net.http.HttpClient.Version#HTTP_1_1
   * @see java.net.http.HttpClient.Redirect#NEVER
   */
  public static HttpClient defaultClient() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }
}
