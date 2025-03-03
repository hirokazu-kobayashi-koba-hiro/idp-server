package org.idp.server.core.type.oidc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Claims */
public class Claims {
  Set<String> values;

  public Claims() {
    this.values = new HashSet<>();
  }

  public Claims(Set<String> values) {
    this.values = values;
  }

  public Claims(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public String toStringValues() {
    return String.join(" ", values);
  }

  public boolean contains(String claim) {
    return values.contains(claim);
  }
}
