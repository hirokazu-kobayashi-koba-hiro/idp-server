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

package org.idp.server.notification.push.apns;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.notification.NotificationTemplate;

public class ApnsConfiguration implements JsonReadable {

  String keyId;
  String teamId;
  String bundleId;
  String keyContent;
  boolean production;
  Map<String, NotificationTemplate> templates;

  public ApnsConfiguration() {}

  public ApnsConfiguration(
      String keyId,
      String teamId,
      String bundleId,
      String keyContent,
      boolean production,
      Map<String, NotificationTemplate> templates) {
    this.keyId = keyId;
    this.teamId = teamId;
    this.bundleId = bundleId;
    this.keyContent = keyContent;
    this.production = production;
    this.templates = templates;
  }

  public String keyId() {
    return keyId;
  }

  public String teamId() {
    return teamId;
  }

  public String keyContent() {
    return keyContent;
  }

  public String bundleId() {
    return bundleId;
  }

  public boolean isProduction() {
    return production;
  }

  public NotificationTemplate findTemplate(String key) {
    return templates.getOrDefault(key, new NotificationTemplate());
  }
}
