package org.idp.server.type;

import java.util.HashSet;
import java.util.Set;

/** AcrValues */
public class AcrValues {

  Set<String> values;

  public AcrValues() {
    this.values = new HashSet<>();
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
