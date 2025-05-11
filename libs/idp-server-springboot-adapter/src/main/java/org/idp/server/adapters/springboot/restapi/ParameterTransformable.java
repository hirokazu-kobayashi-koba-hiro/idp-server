package org.idp.server.adapters.springboot.restapi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.idp.server.basic.type.security.RequestAttributes;
import org.springframework.util.MultiValueMap;

public interface ParameterTransformable {

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

    return new RequestAttributes(contents);
  }
}
