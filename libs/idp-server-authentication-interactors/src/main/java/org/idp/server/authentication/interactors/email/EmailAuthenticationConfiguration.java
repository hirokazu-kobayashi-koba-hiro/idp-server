/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.email;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.platform.notification.EmailSenderSetting;
import org.idp.server.platform.notification.EmailSenderType;

public class EmailAuthenticationConfiguration implements JsonReadable {
  String sender;
  String type;
  Map<String, Map<String, Object>> settings;
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

  public EmailSenderType senderType() {
    return EmailSenderType.of(type);
  }

  public String sender() {
    return sender;
  }

  public EmailVerificationTemplate findTemplate(String templateKey) {
    return templates.getOrDefault(templateKey, new EmailVerificationTemplate());
  }

  public EmailSenderSetting setting() {

    return new EmailSenderSetting(settings.get(type));
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
