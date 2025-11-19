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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Filter for logging HTTP request/response details for OAuth/OIDC endpoints.
 *
 * <p>Enables detailed debugging of HTTP communication during integration testing with external
 * services. Helps identify parameter mismatches, format differences, and unexpected values.
 *
 * <h2>Features</h2>
 *
 * <ul>
 *   <li>Configurable via application.properties
 *   <li>Multi-layer protection: Property + Log Level + Masking
 *   <li>Performance-aware: Skip wrapper creation when disabled
 *   <li>Selective endpoint logging
 *   <li>Automatic token masking for security
 * </ul>
 *
 * <h2>Configuration</h2>
 *
 * <pre>
 * # application.properties
 * idp.logging.request-response.enabled=true
 * idp.logging.request-response.mask-tokens=true
 * idp.logging.request-response.endpoints=/v1/token,/v1/authorizations
 * logging.level.org.idp.server.adapters.springboot.logging.RequestResponseLoggingFilter=DEBUG
 * </pre>
 *
 * @see RequestResponseLoggingProperties
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

  private static final LoggerWrapper logger =
      LoggerWrapper.getLogger(RequestResponseLoggingFilter.class);

  private final RequestResponseLoggingProperties properties;

  public RequestResponseLoggingFilter(RequestResponseLoggingProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!properties.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!isTargetEndpoint(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }

    if (logger.isDebugEnabled()) {
      ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
      ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

      try {
        filterChain.doFilter(wrappedRequest, wrappedResponse);
      } finally {
        logRequest(wrappedRequest);
        logResponse(wrappedResponse);
        wrappedResponse.copyBodyToResponse();
      }
    } else {
      filterChain.doFilter(request, response);
    }
  }

  private boolean isTargetEndpoint(String uri) {
    List<String> endpoints = properties.getEndpoints();
    if (endpoints.isEmpty()) {
      return true;
    }
    return endpoints.stream().anyMatch(uri::contains);
  }

  private void logRequest(ContentCachingRequestWrapper request) {
    String body = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);

    if (body.length() > properties.getMaxBodySize()) {
      body = body.substring(0, properties.getMaxBodySize()) + "... (truncated)";
    }

    logger.debug(
        "[REQUEST] {} {}\nHeaders: {}\nBody: {}",
        request.getMethod(),
        request.getRequestURI(),
        formatHeaders(request),
        properties.isMaskTokens() ? maskSensitiveData(body) : body);
  }

  private void logResponse(ContentCachingResponseWrapper response) {
    String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);

    if (body.length() > properties.getMaxBodySize()) {
      body = body.substring(0, properties.getMaxBodySize()) + "... (truncated)";
    }

    logger.debug(
        "[RESPONSE] {} {}\nHeaders: {}\nBody: {}",
        response.getStatus(),
        HttpStatus.valueOf(response.getStatus()).getReasonPhrase(),
        formatResponseHeaders(response),
        properties.isMaskTokens() ? maskSensitiveData(body) : body);
  }

  private String formatHeaders(HttpServletRequest request) {
    return Collections.list(request.getHeaderNames()).stream()
        .map(name -> name + ": " + maskHeader(name, request.getHeader(name)))
        .collect(Collectors.joining("\n  ", "\n  ", ""));
  }

  private String formatResponseHeaders(ContentCachingResponseWrapper response) {
    return response.getHeaderNames().stream()
        .map(name -> name + ": " + response.getHeader(name))
        .collect(Collectors.joining("\n  ", "\n  ", ""));
  }

  private String maskHeader(String name, String value) {
    if ("Authorization".equalsIgnoreCase(name) && value != null && !value.isEmpty()) {
      String[] parts = value.split(" ", 2);
      if (parts.length == 2) {
        return parts[0] + " ***MASKED***";
      }
    }
    return value;
  }

  private String maskSensitiveData(String data) {
    if (data == null || data.isEmpty()) {
      return data;
    }

    String masked = data;
    for (String param : properties.getMaskParameters()) {
      masked = masked.replaceAll(param + "=([^&\\s]+)", param + "=***MASKED***");
      masked =
          masked.replaceAll(
              "\"" + param + "\"\\s*:\\s*\"([^\"]+)\"", "\"" + param + "\":\"***MASKED***\"");
    }
    return masked;
  }
}
