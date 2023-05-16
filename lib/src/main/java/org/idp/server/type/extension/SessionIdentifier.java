package org.idp.server.type.extension;

import java.util.Objects;

public class SessionIdentifier {
  String value;

  public SessionIdentifier() {}

  public SessionIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
