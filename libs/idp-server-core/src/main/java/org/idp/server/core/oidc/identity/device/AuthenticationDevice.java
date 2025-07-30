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
import org.idp.server.platform.notification.NotificationChannel;
import org.idp.server.platform.notification.NotificationToken;
import org.idp.server.platform.uuid.UuidConvertable;

public class AuthenticationDevice implements Serializable, JsonReadable, UuidConvertable {
  String id;
  String appName;
  String platform;
  String os;
  String model;
  String locale;
  String notificationChannel;
  String notificationToken;
  List<String> availableMethods;
  Integer priority;

  public AuthenticationDevice() {}

  public AuthenticationDevice(
      String id,
      String appName,
      String platform,
      String os,
      String model,
      String locale,
      String notificationChannel,
      String notificationToken,
      List<String> availableMethods,
      Integer priority) {
    this.id = id;
    this.appName = appName;
    this.platform = platform;
    this.os = os;
    this.model = model;
    this.locale = locale;
    this.notificationChannel = notificationChannel;
    this.notificationToken = notificationToken;
    this.availableMethods = availableMethods;
    this.priority = priority;
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

  public String locale() {
    return locale;
  }

  public boolean hasLocale() {
    return locale != null && !locale.isEmpty();
  }

  public NotificationChannel optNotificationChannel(String defaultChannel) {
    if (notificationChannel == null) {
      return new NotificationChannel(defaultChannel);
    }
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

  public Integer priority() {
    // fallback value
    return Objects.requireNonNullElse(priority, 100);
  }

  public boolean hasPriority() {
    return priority != null;
  }

  public AuthenticationDevice withAvailableMethod(String method) {
    List<String> newAvailableMethods = new ArrayList<>(availableMethods);
    newAvailableMethods.add(method);
    return new AuthenticationDevice(
        id,
        appName,
        platform,
        os,
        model,
        locale,
        notificationChannel,
        notificationToken,
        newAvailableMethods,
        priority);
  }

  public AuthenticationDevice patchWith(AuthenticationDevice patchDevice) {
    String newAppName = patchDevice.hasAppName() ? patchDevice.appName : appName;
    String newPlatform = patchDevice.hasPlatform() ? patchDevice.platform : platform;
    String newOs = patchDevice.hasOs() ? patchDevice.os : os;
    String newModel = patchDevice.hasModel() ? patchDevice.model : model;
    String newLocale = patchDevice.hasLocale() ? patchDevice.locale : locale;
    String newNotificationChannel =
        patchDevice.hasNotificationChannel()
            ? patchDevice.notificationChannel
            : notificationChannel;
    String newNotificationToken =
        patchDevice.hasNotificationToken() ? patchDevice.notificationToken : notificationToken;
    Integer newPriority = patchDevice.hasPriority() ? patchDevice.priority : priority;
    return new AuthenticationDevice(
        id,
        newAppName,
        newPlatform,
        newOs,
        newModel,
        newLocale,
        newNotificationChannel,
        newNotificationToken,
        availableMethods,
        newPriority);
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
    if (hasLocale()) map.put("locale", locale);
    if (hasNotificationChannel()) map.put("notification_channel", notificationChannel);
    if (hasNotificationToken()) map.put("notification_token", notificationToken);
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    if (hasPriority()) map.put("priority", priority);
    return map;
  }

  public boolean enabledFidoUaf() {
    if (availableMethods != null && !availableMethods.isEmpty()) {
      return availableMethods.contains("fido-uaf");
    }
    return false;
  }
}
