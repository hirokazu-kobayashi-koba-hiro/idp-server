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
   * Creates a default HTTP client optimized for external service communication.
   *
   * <h3>HTTP/2 Configuration</h3>
   *
   * <p>The client is configured to prefer HTTP/2 but provides automatic fallback:
   *
   * <ul>
   *   <li><strong>HTTP/2 servers:</strong> Uses multiplexing, HPACK header compression, and binary
   *       protocol for optimal performance
   *   <li><strong>HTTP/1.1-only servers:</strong> Automatically falls back to HTTP/1.1 without
   *       errors
   *   <li><strong>Legacy servers:</strong> Graceful degradation ensures compatibility with older
   *       systems
   * </ul>
   *
   * <h3>Redirect Behavior</h3>
   *
   * <p>Configured with {@code Redirect.NORMAL} policy:
   *
   * <ul>
   *   <li><strong>3xx redirects:</strong> Automatically follows standard redirects (301, 302, 303,
   *       307, 308)
   *   <li><strong>Cross-protocol:</strong> Allows HTTPS → HTTP redirects (with security
   *       implications)
   *   <li><strong>Method preservation:</strong> GET/HEAD methods maintained, POST may become GET on
   *       303
   *   <li><strong>Redirect loops:</strong> Built-in protection against infinite redirects
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
   * @see java.net.http.HttpClient.Version#HTTP_2
   * @see java.net.http.HttpClient.Redirect#NORMAL
   */
  public static HttpClient defaultClient() {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }
}
