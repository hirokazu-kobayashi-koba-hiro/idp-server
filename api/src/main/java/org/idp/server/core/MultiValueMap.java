package org.idp.server.core;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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
    if (values.containsKey(key)) {
      return null;
    }
    return values.get(key)[0];
  }

  public boolean contains(String key) {
    return values.containsKey(key);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

}
