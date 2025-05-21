package org.idp.server.core.oidc.identity.device;

import java.util.Objects;

public class NotificationChannel {
  String name;

  public NotificationChannel() {}

  public NotificationChannel(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    NotificationChannel that = (NotificationChannel) o;
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
