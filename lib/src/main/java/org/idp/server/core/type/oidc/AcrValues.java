package org.idp.server.core.type.oidc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** AcrValues */
public class AcrValues {

  Set<String> values;

  public AcrValues() {
    this.values = new HashSet<>();
  }

  public AcrValues(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
    ;
  }

  public AcrValues(Set<String> values) {
    this.values = values;
  }

  public Set<String> values() {
    return values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }
}
