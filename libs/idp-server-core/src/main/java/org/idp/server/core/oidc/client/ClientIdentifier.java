package org.idp.server.core.oidc.client;

import java.util.Objects;

public class ClientIdentifier {
  String value;

  public ClientIdentifier() {}

  public ClientIdentifier(String value) {
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
    if (o == null || getClass() != o.getClass())
      return false;
    ClientIdentifier that = (ClientIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
