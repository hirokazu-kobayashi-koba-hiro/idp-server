package org.idp.server.adapters.springboot.presentation.api;

import java.util.*;
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
}
