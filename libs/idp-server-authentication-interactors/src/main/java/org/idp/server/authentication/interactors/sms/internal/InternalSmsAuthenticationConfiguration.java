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

package org.idp.server.authentication.interactors.sms.internal;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.notification.sms.SmsSenderType;

public class InternalSmsAuthenticationConfiguration implements JsonReadable {
  String type;
  Map<String, Map<String, Object>> settings;
  Map<String, SmslVerificationTemplate> templates;
  int retryCountLimitation;
  int expireSeconds;

  public InternalSmsAuthenticationConfiguration() {}

  public InternalSmsAuthenticationConfiguration(
      Map<String, SmslVerificationTemplate> templates,
      int retryCountLimitation,
      int expireSeconds) {
    this.templates = templates;
    this.retryCountLimitation = retryCountLimitation;
    this.expireSeconds = expireSeconds;
  }

  public SmsSenderType senderType() {
    return new SmsSenderType(type);
  }

  public SmslVerificationTemplate findTemplate(String templateKey) {
    return templates.getOrDefault(templateKey, new SmslVerificationTemplate());
  }

  public Map<String, Object> settings(String templateKey) {
    return settings.getOrDefault(templateKey, Map.of());
  }

  public int retryCountLimitation() {
    return retryCountLimitation;
  }

  public int expireSeconds() {
    return expireSeconds;
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }
}
