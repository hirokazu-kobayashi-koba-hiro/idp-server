package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

public class DocType {
  String value;

  public DocType() {}

  public DocType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
