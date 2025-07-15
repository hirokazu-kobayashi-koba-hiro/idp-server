/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.identity.device;

import java.io.Serializable;
import java.util.*;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuthenticationDevice implements Serializable, JsonReadable, UuidConvertable {
  String id;
  String appName;
  String platform;
  String os;
  String model;
  String notificationChannel;
  String notificationToken;
  List<String> availableMethods;
  boolean preferredForNotification;

  public AuthenticationDevice() {}

  public AuthenticationDevice(
      String id,
      String appName,
      String platform,
      String os,
      String model,
      String notificationChannel,
      String notificationToken,
      List<String> availableMethods,
      boolean preferredForNotification) {
    this.id = id;
    this.appName = appName;
    this.platform = platform;
    this.os = os;
    this.model = model;
    this.notificationChannel = notificationChannel;
    this.notificationToken = notificationToken;
    this.availableMethods = availableMethods;
    this.preferredForNotification = preferredForNotification;
  }

  public String id() {
    return id;
  }

  public UUID idAsUuid() {
    return convertUuid(id);
  }

  public String appName() {
    return appName;
  }

  public boolean hasAppName() {
    return appName != null && !appName.isEmpty();
  }

  public String platform() {
    return platform;
  }

  public boolean hasPlatform() {
    return platform != null && !platform.isEmpty();
  }

  public String os() {
    return os;
  }

  public boolean hasOs() {
    return os != null && !os.isEmpty();
  }

  public String model() {
    return model;
  }

  public boolean hasModel() {
    return model != null && !model.isEmpty();
  }

  public NotificationChannel notificationChannel() {
    return new NotificationChannel(notificationChannel);
  }

  public boolean hasNotificationChannel() {
    return notificationChannel != null && !notificationChannel.isEmpty();
  }

  public NotificationToken notificationToken() {
    return new NotificationToken(notificationToken);
  }

  public boolean hasNotificationToken() {
    return notificationToken != null && !notificationToken.isEmpty();
  }

  public List<String> availableMethods() {
    return availableMethods;
  }

  public boolean hasAvailableMethods() {
    return availableMethods != null && !availableMethods.isEmpty();
  }

  public AuthenticationDevice withAvailableMethod(String method) {
    List<String> newAvailableMethods = new ArrayList<>(availableMethods);
    newAvailableMethods.add(method);
    return new AuthenticationDevice(
        id,
        appName,
        platform,
        os,
        method,
        notificationChannel,
        notificationToken,
        newAvailableMethods,
        preferredForNotification);
  }

  public boolean isPreferredForNotification() {
    return preferredForNotification;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    if (hasAppName()) map.put("app_name", appName);
    if (hasPlatform()) map.put("platform", platform);
    if (hasOs()) map.put("os", os);
    if (hasModel()) map.put("model", model);
    if (hasNotificationChannel()) map.put("notification_channel", notificationChannel);
    if (hasNotificationToken()) map.put("notification_token", notificationToken);
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    map.put("preferred_for_notification", preferredForNotification);
    return map;
  }
}
