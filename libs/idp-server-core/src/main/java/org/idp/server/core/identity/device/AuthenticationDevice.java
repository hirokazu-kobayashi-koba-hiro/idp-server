package org.idp.server.core.identity.device;

import java.io.Serializable;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.authentication.notification.device.NotificationChannel;
import org.idp.server.core.authentication.notification.device.NotificationToken;

public class AuthenticationDevice implements Serializable, JsonReadable {
  String id;
  String platform;
  String os;
  String model;
  String notificationChannel;
  String notificationToken;
  boolean preferredForNotification;

  public AuthenticationDevice() {}

  public AuthenticationDevice(
      String id,
      String platform,
      String os,
      String model,
      String notificationChannel,
      String notificationToken,
      boolean preferredForNotification) {
    this.id = id;
    this.platform = platform;
    this.os = os;
    this.model = model;
    this.notificationChannel = notificationChannel;
    this.notificationToken = notificationToken;
    this.preferredForNotification = preferredForNotification;
  }

  public String id() {
    return id;
  }

  public String platform() {
    return platform;
  }

  public String os() {
    return os;
  }

  public String model() {
    return model;
  }

  public NotificationChannel notificationChannel() {
    return new NotificationChannel(notificationChannel);
  }

  public NotificationToken notificationToken() {
    return new NotificationToken(notificationToken);
  }

  public boolean isPreferredForNotification() {
    return preferredForNotification;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
