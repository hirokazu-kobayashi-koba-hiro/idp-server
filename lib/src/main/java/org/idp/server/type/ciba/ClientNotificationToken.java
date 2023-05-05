package org.idp.server.type.ciba;

import java.util.Objects;

public class ClientNotificationToken {
  String value;

  public ClientNotificationToken() {}

  public ClientNotificationToken(String value) {
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
    ClientNotificationToken that = (ClientNotificationToken) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
