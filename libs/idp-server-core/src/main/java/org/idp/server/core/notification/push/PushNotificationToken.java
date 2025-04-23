package org.idp.server.core.notification.push;

import java.util.Objects;

public class PushNotificationToken {
  String value;

  public PushNotificationToken() {}

  public PushNotificationToken(String value) {
    this.value = value;
  }

  public String name() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    PushNotificationToken that = (PushNotificationToken) o;
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
