package org.idp.server.core.notification.push;

import java.util.Objects;

public class PushNotificationChannel {
  String name;

  public PushNotificationChannel() {}

  public PushNotificationChannel(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    PushNotificationChannel that = (PushNotificationChannel) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }
}
