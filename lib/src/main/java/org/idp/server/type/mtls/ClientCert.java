package org.idp.server.type.mtls;

import java.util.Objects;

public class ClientCert {
  String value;

  public ClientCert() {}

  public ClientCert(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
