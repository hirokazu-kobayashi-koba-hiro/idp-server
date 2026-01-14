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

package org.idp.server.authentication.interactors.sms;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.authentication.interactors.sms.executor.SmslVerificationTemplate;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.notification.sms.SmsSenderType;

public class SmsAuthenticationConfiguration implements JsonReadable {
  String senderType;
  Map<String, Object> settings = new HashMap<>();
  Map<String, SmslVerificationTemplate> templates = new HashMap<>();
  int retryCountLimitation = 5;
  int expireSeconds = 300;

  public SmsAuthenticationConfiguration() {}

  public SmsAuthenticationConfiguration(
      Map<String, SmslVerificationTemplate> templates,
      int retryCountLimitation,
      int expireSeconds) {
    this.templates = templates;
    this.retryCountLimitation = retryCountLimitation;
    this.expireSeconds = expireSeconds;
  }

  public SmsSenderType senderType() {
    return new SmsSenderType(senderType);
  }

  public SmslVerificationTemplate findTemplate(String templateKey) {
    if (templates == null) {
      return defaultTemplate();
    }
    return templates.getOrDefault(templateKey, defaultTemplate());
  }

  private SmslVerificationTemplate defaultTemplate() {
    return new SmslVerificationTemplate(
        "Verification Code",
        "Your verification code is: {VERIFICATION_CODE}. Expires in {EXPIRE_SECONDS} seconds.");
  }

  public Map<String, Object> settings() {
    return settings;
  }

  public int retryCountLimitation() {
    return retryCountLimitation;
  }

  public int expireSeconds() {
    return expireSeconds;
  }

  public boolean exists() {
    return senderType != null && !senderType.isEmpty();
  }
}
