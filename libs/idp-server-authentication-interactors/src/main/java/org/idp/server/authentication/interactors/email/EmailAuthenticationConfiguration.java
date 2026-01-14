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

package org.idp.server.authentication.interactors.email;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.notification.email.EmailSenderConfiguration;

public class EmailAuthenticationConfiguration implements JsonReadable {
  String sender;
  String function;
  EmailSenderConfiguration senderConfig = new EmailSenderConfiguration();
  Map<String, EmailVerificationTemplate> templates;
  int retryCountLimitation;
  int expireSeconds;

  public EmailAuthenticationConfiguration() {}

  public EmailAuthenticationConfiguration(
      String sender,
      Map<String, EmailVerificationTemplate> templates,
      int retryCountLimitation,
      int expireSeconds) {
    this.sender = sender;
    this.templates = templates;
    this.retryCountLimitation = retryCountLimitation;
    this.expireSeconds = expireSeconds;
  }

  public String function() {
    return function;
  }

  public String sender() {
    return sender;
  }

  public EmailVerificationTemplate findTemplate(String templateKey) {
    if (templates == null) {
      return defaultTemplate();
    }
    return templates.getOrDefault(templateKey, defaultTemplate());
  }

  private EmailVerificationTemplate defaultTemplate() {
    return new EmailVerificationTemplate(
        "Verification Code",
        "Your verification code is: {VERIFICATION_CODE}\nThis code expires in {EXPIRE_SECONDS} seconds.");
  }

  public EmailSenderConfiguration senderConfig() {
    if (senderConfig == null) {
      return new EmailSenderConfiguration();
    }
    return senderConfig;
  }

  public int retryCountLimitation() {
    return retryCountLimitation;
  }

  public int expireSeconds() {
    return expireSeconds;
  }

  public boolean exists() {
    return sender != null && !sender.isEmpty();
  }
}
