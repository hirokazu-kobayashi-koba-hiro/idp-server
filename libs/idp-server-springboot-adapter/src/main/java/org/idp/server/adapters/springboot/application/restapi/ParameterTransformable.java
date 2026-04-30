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

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.util.MultiValueMap;

public interface ParameterTransformable extends AuthorizationHeaderHandlerable {

  default Map<String, String[]> transform(MultiValueMap<String, String> request) {
    HashMap<String, String[]> map = new HashMap<>();
    if (Objects.isNull(request)) {
      return map;
    }
    Set<Map.Entry<String, List<String>>> entries = request.entrySet();
    entries.forEach(entry -> map.put(entry.getKey(), entry.getValue().toArray(new String[0])));
    return map;
  }

  default RequestAttributes transform(HttpServletRequest request) {

    // Use resolved IP from TrustedProxyFilter if available, fallback to remoteAddr
    String ip =
        Optional.ofNullable((String) request.getAttribute("resolvedClientIp"))
            .orElse(request.getRemoteAddr());

    String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");

    Map<String, Object> contents = new HashMap<>();
    contents.put("ip_address", ip);
    contents.put("user_agent", userAgent);
    contents.put("resource", request.getRequestURI());
    contents.put("action", request.getMethod());
    contents.put("request_url", resolveRequestUrl(request));

    Enumeration<String> headerNames = request.getHeaderNames();
    Map<String, String> headers = new HashMap<>();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      String headerValue = request.getHeader(headerName);
      headers.put(headerName, headerValue);
    }
    contents.put("headers", headers);

    return new RequestAttributes(contents);
  }

  /**
   * Resolves the full request URL, considering reverse proxy headers.
   *
   * <p>When the application runs behind a reverse proxy (e.g., nginx), {@code
   * request.getRequestURL()} returns the internal URL (http://...) instead of the external URL
   * (https://...). This method uses {@code X-Forwarded-Proto} and {@code X-Forwarded-Host} headers
   * to reconstruct the original request URL as seen by the client.
   *
   * <p>This is critical for DPoP (RFC 9449) htu claim verification, where the server must compare
   * the htu claim against the actual request URI as perceived by the client.
   */
  default String resolveRequestUrl(HttpServletRequest request) {
    String forwardedProto = request.getHeader("X-Forwarded-Proto");
    String forwardedHost = request.getHeader("X-Forwarded-Host");

    if (forwardedProto != null && !forwardedProto.isEmpty()) {
      String host =
          (forwardedHost != null && !forwardedHost.isEmpty())
              ? forwardedHost
              : request.getServerName();
      return forwardedProto + "://" + host + request.getRequestURI();
    }

    return request.getRequestURL().toString();
  }

  /**
   * Extracts all values of the {@code DPoP} HTTP header.
   *
   * <p>The single-header invariant (RFC 9449 Section 4.3 Check 1) is enforced in the core layer by
   * {@code DPoPHeaderValidator}. This method only handles the HTTP transport concern of enumerating
   * header values.
   */
  default List<String> extractDPoPProofHeaders(HttpServletRequest request) {
    Enumeration<String> headers = request.getHeaders("DPoP");
    if (headers == null) {
      return List.of();
    }
    return Collections.list(headers);
  }
}
