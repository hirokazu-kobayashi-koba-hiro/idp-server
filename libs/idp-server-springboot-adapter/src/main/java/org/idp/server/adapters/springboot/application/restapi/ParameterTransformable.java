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
   * <p>When the application runs behind a reverse proxy (e.g., nginx, or an AWS API Gateway + ALB
   * in front of the service), {@code request.getRequestURL()} returns the internal URL (http://...)
   * instead of the external URL (https://...) the client actually used. This method uses {@code
   * X-Forwarded-Proto} and {@code X-Forwarded-Host} to reconstruct the client-facing URL.
   *
   * <p>This is critical for DPoP (RFC 9449) htu claim verification, where the server must compare
   * the htu claim against the request URI as perceived by the client.
   *
   * <p><b>Trust boundary.</b> The {@code X-Forwarded-Proto} / {@code X-Forwarded-Host} values are
   * used as received, so the integrity of the reconstructed htu relies on {@code idp-server} being
   * reachable <b>only</b> through the trusted ingress (reverse proxy / API Gateway + ALB), never
   * directly by clients. That network isolation (private subnet + security groups, or the proxy's
   * upstream ACL) is the trust boundary for these headers. Note this is distinct from the {@code
   * trusted_proxies} configuration, which gates {@code X-Forwarded-For} for client-IP resolution
   * only and does not protect the htu reconstruction. An application-level source-IP allowlist is
   * intentionally not applied to the htu headers: the immediate peer seen by the service in cloud
   * deployments (e.g. an AWS VPC Link / internal load balancer) is a dynamic internal address, and
   * gating on it would <b>silently break all DPoP verification</b> whenever that address falls
   * outside the configured CIDR (e.g. a default-VPC {@code 172.31/16} peer against a {@code
   * 10.0.0.0/8} allowlist) — an availability risk that outweighs the marginal gain over the network
   * isolation that must already be in place. Do not expose {@code idp-server} directly to untrusted
   * networks.
   *
   * <p>When a chain of proxies is present, {@code X-Forwarded-Proto} / {@code X-Forwarded-Host}
   * accumulate as {@code "original, next, ..."}; the leftmost (client-facing) value is used. Using
   * the raw comma-joined value would corrupt the reconstructed URL and make htu never match.
   */
  default String resolveRequestUrl(HttpServletRequest request) {
    String forwardedProto = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));

    if (forwardedProto != null && !forwardedProto.isEmpty()) {
      String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
      String host =
          (forwardedHost != null && !forwardedHost.isEmpty())
              ? forwardedHost
              : request.getServerName();
      return forwardedProto + "://" + host + request.getRequestURI();
    }

    return request.getRequestURL().toString();
  }

  /**
   * Returns the first (client-facing) value of a possibly comma-separated {@code X-Forwarded-*}
   * header. Returns {@code null} for a {@code null} input.
   */
  private static String firstForwardedValue(String headerValue) {
    if (headerValue == null) {
      return null;
    }
    int comma = headerValue.indexOf(',');
    return (comma >= 0 ? headerValue.substring(0, comma) : headerValue).trim();
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
