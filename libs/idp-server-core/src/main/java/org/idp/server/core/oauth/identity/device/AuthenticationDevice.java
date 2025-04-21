package org.idp.server.core.oauth.identity.device;

import java.io.Serializable;
import org.idp.server.core.basic.json.JsonReadable;

public class AuthenticationDevice implements Serializable, JsonReadable {
  String id;
  String platform;
  String os;
  String model;
  String notificationToken;
  boolean preferredForNotification;

  public AuthenticationDevice() {}

  public AuthenticationDevice(
      String id,
      String platform,
      String os,
      String model,
      String notificationToken,
      boolean preferredForNotification) {
    this.id = id;
    this.platform = platform;
    this.os = os;
    this.model = model;
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

  public String notificationToken() {
    return notificationToken;
  }

  public boolean isPreferredForNotification() {
    return preferredForNotification;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
