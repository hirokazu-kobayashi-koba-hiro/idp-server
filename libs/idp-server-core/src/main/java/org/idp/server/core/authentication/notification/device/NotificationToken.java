package org.idp.server.core.authentication.notification.device;

import java.util.Objects;

public class NotificationToken {
  String value;

  public NotificationToken() {}

  public NotificationToken(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    NotificationToken that = (NotificationToken) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
