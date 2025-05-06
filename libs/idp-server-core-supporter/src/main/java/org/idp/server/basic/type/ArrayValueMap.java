package org.idp.server.basic.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ArrayValueMap */
public class ArrayValueMap {
  Map<String, String[]> values;

  public ArrayValueMap() {
    this.values = new HashMap<>();
  }

  public ArrayValueMap(Map<String, String[]> values) {
    this.values = values;
  }

  public String getFirstOrEmpty(String key) {
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
    values.forEach((key, value) -> {
      if (value.length > 1) {
        keys.add(key);
      }
    });
    return keys;
  }

  public Map<String, String> singleValueMap() {
    Map<String, String> map = new HashMap<>();
    values.forEach((key, value) -> {
      map.put(key, value[0]);
    });
    return map;
  }
}
