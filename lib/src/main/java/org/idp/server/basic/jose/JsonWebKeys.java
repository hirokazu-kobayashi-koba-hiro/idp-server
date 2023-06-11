package org.idp.server.basic.jose;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

  public JsonWebKey find(String keyId) {
    return values.stream()
        .filter(value -> value.keyId().equals(keyId))
        .findFirst()
        .orElse(new JsonWebKey());
  }

  public JsonWebKey findFirst(String algorithm) {
    return values.stream()
        .filter(value -> value.algorithm().equals(algorithm))
        .findFirst()
        .orElse(new JsonWebKey());
  }
}
