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
import org.idp.server.core.oidc.token.AuthorizationHeaderHandlerable;
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

    String ip =
        Optional.ofNullable(request.getHeader("X-Forwarded-For"))
            .map(s -> s.split(",")[0].trim())
            .orElse(request.getRemoteAddr());

    String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");

    Map<String, Object> contents = new HashMap<>();
    contents.put("ip_address", ip);
    contents.put("user_agent", userAgent);
    contents.put("resource", request.getRequestURI());
    contents.put("action", request.getMethod());

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
}
