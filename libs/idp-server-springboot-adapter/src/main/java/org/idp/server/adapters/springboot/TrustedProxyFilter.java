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

package org.idp.server.adapters.springboot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.system.SystemConfiguration;
import org.idp.server.platform.system.SystemConfigurationApi;
import org.idp.server.platform.system.config.TrustedProxyConfig;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter for resolving the real client IP address from trusted proxies.
 *
 * <p>When the application runs behind a reverse proxy or load balancer, this filter extracts the
 * real client IP from the X-Forwarded-For header, but only if the request comes from a trusted
 * proxy.
 *
 * <h2>Security</h2>
 *
 * <p>The X-Forwarded-For header can be spoofed by clients. This filter only trusts the header when:
 *
 * <ul>
 *   <li>Trusted proxy configuration is enabled
 *   <li>The request's remote address matches a configured trusted proxy
 * </ul>
 *
 * <h2>Request Attribute</h2>
 *
 * <p>The resolved client IP is stored as a request attribute: {@code resolvedClientIp}. Downstream
 * filters and controllers can retrieve it via {@code request.getAttribute("resolvedClientIp")}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedProxyFilter extends OncePerRequestFilter {

  public static final String RESOLVED_CLIENT_IP_ATTRIBUTE = "resolvedClientIp";

  private static final LoggerWrapper logger = LoggerWrapper.getLogger(TrustedProxyFilter.class);

  private final SystemConfigurationApi systemConfigurationApi;

  public TrustedProxyFilter(IdpServerApplication idpServerApplication) {
    this.systemConfigurationApi = idpServerApplication.systemConfigurationApi();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String resolvedIp = resolveClientIp(request);
    request.setAttribute(RESOLVED_CLIENT_IP_ATTRIBUTE, resolvedIp);

    filterChain.doFilter(request, response);
  }

  /**
   * Resolves the real client IP address.
   *
   * <p>If the request comes from a trusted proxy and X-Forwarded-For header is present, extracts
   * the first IP from the header (the original client). Otherwise, returns the remote address.
   *
   * @param request the HTTP request
   * @return the resolved client IP address
   */
  private String resolveClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();

    SystemConfiguration config = systemConfigurationApi.get();
    TrustedProxyConfig trustedProxyConfig = config.trustedProxies();

    if (!trustedProxyConfig.isEnabled()) {
      logger.debug("Trusted proxy not enabled, using remoteAddr: {}", remoteAddr);
      return remoteAddr;
    }

    if (!trustedProxyConfig.isTrustedProxy(remoteAddr)) {
      logger.debug("Request from {} is not from trusted proxy, using remoteAddr", remoteAddr);
      return remoteAddr;
    }

    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor == null || xForwardedFor.isBlank()) {
      logger.debug("X-Forwarded-For header not present, using remoteAddr: {}", remoteAddr);
      return remoteAddr;
    }

    // X-Forwarded-For format: client, proxy1, proxy2, ...
    // The first IP is the original client
    String clientIp = xForwardedFor.split(",")[0].trim();
    logger.debug(
        "Resolved client IP from X-Forwarded-For: {} (remoteAddr was: {})", clientIp, remoteAddr);

    return clientIp;
  }
}
