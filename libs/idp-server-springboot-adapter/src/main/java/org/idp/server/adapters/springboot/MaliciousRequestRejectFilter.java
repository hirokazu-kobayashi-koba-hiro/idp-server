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
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that rejects HTTP requests containing null bytes (0x00) in parameters or body.
 *
 * <p>PostgreSQL does not accept null bytes in UTF-8 strings, which causes 500 Internal Server
 * Error. This filter detects null bytes early and returns 400 Bad Request with a security log.
 *
 * <p>Protects all API endpoints uniformly:
 *
 * <ul>
 *   <li>Query parameters and form-urlencoded body: checked via {@code getParameterNames()}
 *   <li>JSON/other request body: checked by reading the InputStream before dispatching
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class MaliciousRequestRejectFilter extends OncePerRequestFilter {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(MaliciousRequestRejectFilter.class);
  private static final int MAX_BODY_SIZE = 10 * 1024 * 1024;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // Check query parameters and form-urlencoded body parameters
    String detectedParam = findNullByteInParameters(request);
    if (detectedParam != null) {
      rejectRequest(request, response, detectedParam);
      return;
    }

    // Check raw query string (in case servlet container normalizes null bytes in parameter parsing)
    String queryString = request.getQueryString();
    if (queryString != null && queryString.indexOf('\0') >= 0) {
      rejectRequest(request, response, "query_string");
      return;
    }

    // For non-form-urlencoded body (e.g. JSON), read and check the body
    if (hasNonFormBody(request)) {
      int contentLength = request.getContentLength();
      if (contentLength > MAX_BODY_SIZE) {
        rejectPayloadTooLarge(request, response);
        return;
      }
      byte[] body = request.getInputStream().readAllBytes();
      if (containsNullByte(body)) {
        rejectRequest(request, response, "request_body");
        return;
      }
      filterChain.doFilter(new CachedBodyRequestWrapper(request, body), response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String findNullByteInParameters(HttpServletRequest request) {
    Enumeration<String> paramNames = request.getParameterNames();
    while (paramNames.hasMoreElements()) {
      String name = paramNames.nextElement();
      if (name != null && name.indexOf('\0') >= 0) {
        return name;
      }
      String[] values = request.getParameterValues(name);
      if (values != null) {
        for (String value : values) {
          if (value != null && value.indexOf('\0') >= 0) {
            return name;
          }
        }
      }
    }
    return null;
  }

  private boolean containsNullByte(byte[] data) {
    for (byte b : data) {
      if (b == 0) {
        return true;
      }
    }
    return false;
  }

  private boolean hasNonFormBody(HttpServletRequest request) {
    String method = request.getMethod();
    boolean hasBodyMethod =
        "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method);
    if (!hasBodyMethod) {
      return false;
    }
    int contentLength = request.getContentLength();
    if (contentLength <= 0) {
      return false;
    }
    // form-urlencoded body is already covered by getParameterNames() above
    String contentType = request.getContentType();
    return contentType != null
        && !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded");
  }

  private void rejectPayloadTooLarge(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    log.warn(
        "Request body too large: content_length={}, uri={}, method={}",
        request.getContentLength(),
        request.getRequestURI(),
        request.getMethod());

    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write("{\"error\":\"invalid_request\",\"error_description\":\"Request body too large\"}");
  }

  private void rejectRequest(
      HttpServletRequest request, HttpServletResponse response, String paramName)
      throws IOException {
    log.error(
        "Malicious input detected: attack_type=null_byte_injection, input_value={}, uri={}, method={}",
        paramName,
        request.getRequestURI(),
        request.getMethod());

    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write("{\"error\":\"invalid_request\",\"error_description\":\"Invalid parameter value\"}");
  }

  private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
      super(request);
      this.cachedBody = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return byteArrayInputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          // no-op
        }

        @Override
        public int read() {
          return byteArrayInputStream.read();
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(
          new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
      return cachedBody.length;
    }

    @Override
    public long getContentLengthLong() {
      return cachedBody.length;
    }
  }
}
