package org.idp.server.core.basic.jose;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** JsonWebKeys */
public class JsonWebKeys implements Iterable<JsonWebKey> {

  List<JsonWebKey> values;

  public JsonWebKeys() {
    this.values = new ArrayList<>();
  }

  public JsonWebKeys(List<JsonWebKey> values) {
    this.values = values;
  }

  @Override
  public Iterator<JsonWebKey> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public JsonWebKey findBy(String keyId) {
    return values.stream()
        .filter(
            value -> {
              if (Objects.isNull(value.keyId())) {
                return false;
              }
              return value.keyId().equals(keyId);
            })
        .findFirst()
        .orElse(new JsonWebKey());
  }

  public JsonWebKey findByAlgorithm(String algorithm) {
    return values.stream()
        .filter(
            value -> {
              if (Objects.isNull(value.algorithm())) {
                return false;
              }
              return value.algorithm().equals(algorithm);
            })
        .findFirst()
        .orElse(new JsonWebKey());
  }

  public JsonWebKeys filterWithAlgorithm(String algorithm) {
    List<JsonWebKey> list =
        values.stream().filter(value -> value.algorithm().equals(algorithm)).toList();
    return new JsonWebKeys(list);
  }

  public JsonWebKeys filterWithX5c() {
    List<JsonWebKey> list = values.stream().filter(JsonWebKey::hasX5c).toList();
    return new JsonWebKeys(list);
  }

  public JsonWebKey getFirst() {
    return values.get(0);
  }

  public boolean isMultiValues() {
    return values.size() >= 2;
  }
}
