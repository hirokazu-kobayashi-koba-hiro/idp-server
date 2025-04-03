package org.idp.server.core.security.ssf;

import java.util.Objects;

public class SecurityEventTypeIdentifier {
  String value;

  public SecurityEventTypeIdentifier() {}

  public SecurityEventTypeIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
