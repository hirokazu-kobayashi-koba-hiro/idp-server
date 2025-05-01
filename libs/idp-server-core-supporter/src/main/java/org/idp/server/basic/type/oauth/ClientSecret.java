package org.idp.server.basic.type.oauth;

import java.util.Objects;

public class ClientSecret {
  String value;

  public ClientSecret() {}

  public ClientSecret(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientSecret that = (ClientSecret) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public int octetsSize() {
    return value.getBytes().length;
  }
}
