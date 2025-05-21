package org.idp.server.platform.security.hook;

import java.util.Objects;

public class SecurityEventHookConfigurationIdentifier {

  String value;

  public SecurityEventHookConfigurationIdentifier() {}

  public SecurityEventHookConfigurationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SecurityEventHookConfigurationIdentifier that = (SecurityEventHookConfigurationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
