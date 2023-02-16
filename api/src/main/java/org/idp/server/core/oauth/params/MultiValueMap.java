package org.idp.server.core.oauth.params;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** MultiValueMap */
public class MultiValueMap {
  Map<String, String[]> values;

  public MultiValueMap() {
    this.values = new HashMap<>();
  }

  public MultiValueMap(Map<String, String[]> values) {
    this.values = values;
  }

  public String getFirst(String key) {
    if (!values.containsKey(key)) {
      return "";
    }
    return values.get(key)[0];
  }

  public boolean contains(String key) {
    return values.containsKey(key);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public List<String> multiValueKeys() {
    List<String> keys = new ArrayList<>();
    values.forEach(
        (key, value) -> {
          if (value.length > 1) {
            keys.add(key);
          }
        });
    return keys;
  }
}
