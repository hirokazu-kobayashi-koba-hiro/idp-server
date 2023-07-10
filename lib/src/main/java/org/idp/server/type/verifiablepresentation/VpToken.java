package org.idp.server.type.verifiablepresentation;

import java.util.Objects;

public class VpToken {
  String value;

  public VpToken() {}

  public VpToken(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
