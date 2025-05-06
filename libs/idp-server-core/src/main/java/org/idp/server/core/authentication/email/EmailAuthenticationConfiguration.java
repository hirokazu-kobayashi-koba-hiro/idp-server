package org.idp.server.core.authentication.email;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.authentication.notification.EmailSenderType;

public class EmailAuthenticationConfiguration implements JsonReadable {
  String sender;
  String type;
  Map<String, Map<String, Object>> settings;
  Map<String, EmailVerificationTemplate> templates;
  int retryCountLimitation;
  int expireSeconds;

  public EmailAuthenticationConfiguration() {}

  public EmailAuthenticationConfiguration(String sender, Map<String, EmailVerificationTemplate> templates, int retryCountLimitation, int expireSeconds) {
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
