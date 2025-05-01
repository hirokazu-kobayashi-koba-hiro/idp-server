package org.idp.server.basic.type.extension;

import java.util.Objects;

public class JarmPayload {
  String value;

  public JarmPayload() {}

  public JarmPayload(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
