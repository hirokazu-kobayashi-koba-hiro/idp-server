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
package org.idp.server.core.openid.authentication.risk;

import java.time.LocalDateTime;

public class UserKnownDevice {

  String tenantId;
  String userId;
  DeviceFingerprint deviceFingerprint;
  String deviceOs;
  String deviceBrowser;
  String devicePlatform;
  String ipAddress;
  double latitude;
  double longitude;
  String country;
  String city;
  int loginCount;
  LocalDateTime firstSeenAt;
  LocalDateTime lastSeenAt;

  public UserKnownDevice() {}

  public UserKnownDevice(
      String tenantId,
      String userId,
      DeviceFingerprint deviceFingerprint,
      String deviceOs,
      String deviceBrowser,
      String devicePlatform,
      String ipAddress,
      double latitude,
      double longitude,
      String country,
      String city,
      int loginCount,
      LocalDateTime firstSeenAt,
      LocalDateTime lastSeenAt) {
    this.tenantId = tenantId;
    this.userId = userId;
    this.deviceFingerprint = deviceFingerprint;
    this.deviceOs = deviceOs;
    this.deviceBrowser = deviceBrowser;
    this.devicePlatform = devicePlatform;
    this.ipAddress = ipAddress;
    this.latitude = latitude;
    this.longitude = longitude;
    this.country = country;
    this.city = city;
    this.loginCount = loginCount;
    this.firstSeenAt = firstSeenAt;
    this.lastSeenAt = lastSeenAt;
  }

  public String tenantId() {
    return tenantId;
  }

  public String userId() {
    return userId;
  }

  public DeviceFingerprint deviceFingerprint() {
    return deviceFingerprint;
  }

  public String deviceOs() {
    return deviceOs;
  }

  public String deviceBrowser() {
    return deviceBrowser;
  }

  public String devicePlatform() {
    return devicePlatform;
  }

  public String ipAddress() {
    return ipAddress;
  }

  public double latitude() {
    return latitude;
  }

  public double longitude() {
    return longitude;
  }

  public String country() {
    return country;
  }

  public String city() {
    return city;
  }

  public int loginCount() {
    return loginCount;
  }

  public LocalDateTime firstSeenAt() {
    return firstSeenAt;
  }

  public LocalDateTime lastSeenAt() {
    return lastSeenAt;
  }
}
