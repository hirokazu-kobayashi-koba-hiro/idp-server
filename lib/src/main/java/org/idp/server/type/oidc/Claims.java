package org.idp.server.type.oidc;

import java.util.HashSet;
import java.util.Set;

/** Claims */
public class Claims {
  Set<String> values;

  public Claims() {
    this.values = new HashSet<>();
  }

  public Claims(Set<String> values) {
    this.values = values;
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
