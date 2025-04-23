package org.idp.server.core.oauth.identity.device;

import java.io.Serializable;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.notification.push.PushNotificationChannel;
import org.idp.server.core.notification.push.PushNotificationToken;

public class AuthenticationDevice implements Serializable, JsonReadable {
  String id;
  String platform;
  String os;
  String model;
  String pushNotificationChannel;
  String pushNotificationToken;
  boolean preferredForNotification;

  public AuthenticationDevice() {}

  public AuthenticationDevice(
      String id,
      String platform,
      String os,
      String model,
      String pushNotificationChannel,
      String pushNotificationToken,
      boolean preferredForNotification) {
    this.id = id;
    this.platform = platform;
    this.os = os;
    this.model = model;
    this.pushNotificationChannel = pushNotificationChannel;
    this.pushNotificationToken = pushNotificationToken;
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

  public PushNotificationChannel pushNotificationChannel() {
    return new PushNotificationChannel(pushNotificationChannel);
  }

  public PushNotificationToken pushNotificationToken() {
    return new PushNotificationToken(pushNotificationToken);
  }

  public boolean isPreferredForNotification() {
    return preferredForNotification;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
