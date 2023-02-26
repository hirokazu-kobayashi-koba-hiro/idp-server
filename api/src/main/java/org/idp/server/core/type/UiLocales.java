package org.idp.server.core.type;

import java.util.HashSet;
import java.util.Set;

/** UiLocales */
public class UiLocales {

  Set<String> values;

  public UiLocales() {
    this.values = new HashSet<>();
  }

  public UiLocales(Set<String> values) {
    this.values = values;
  }

  public Set<String> values() {
    return values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }
}
