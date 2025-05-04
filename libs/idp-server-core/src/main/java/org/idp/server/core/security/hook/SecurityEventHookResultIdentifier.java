package org.idp.server.core.security.hook;

import java.util.Objects;

public class SecurityEventHookResultIdentifier {

  String value;

  public SecurityEventHookResultIdentifier() {}

  public SecurityEventHookResultIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SecurityEventHookResultIdentifier that = (SecurityEventHookResultIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
