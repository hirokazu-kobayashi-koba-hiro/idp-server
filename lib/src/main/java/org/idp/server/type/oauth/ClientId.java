package org.idp.server.type.oauth;

import java.util.Objects;

/** ClientId */
public class ClientId {
  String value;

  public ClientId() {}

  public ClientId(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientId clientId = (ClientId) o;
    return Objects.equals(value, clientId.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
