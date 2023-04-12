package org.idp.server.type.oauth;

import java.util.*;
import java.util.stream.Collectors;

/** Scopes */
public class Scopes {
  Set<String> values;

  public Scopes() {
    this.values = new HashSet<>();
  }

  public Scopes(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public Scopes(Set<String> values) {
    this.values = values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public String toStringValues() {
    return String.join(" ", values);
  }

  public boolean contains(String scope) {
    return values.contains(scope);
  }
}
