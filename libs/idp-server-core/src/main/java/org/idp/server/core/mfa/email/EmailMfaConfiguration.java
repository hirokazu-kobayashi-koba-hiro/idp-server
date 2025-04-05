package org.idp.server.core.mfa.email;

import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;

public class EmailMfaConfiguration implements JsonReadable {
  String sender;
  Map<String, EmailTemplate> templates;
  int retryCountLimitation;
  int expireSeconds;

  public EmailMfaConfiguration() {}

  public EmailMfaConfiguration(
      String sender,
      Map<String, EmailTemplate> templates,
      int retryCountLimitation,
      int expireSeconds) {
    this.sender = sender;
    this.templates = templates;
    this.retryCountLimitation = retryCountLimitation;
    this.expireSeconds = expireSeconds;
  }

  public String sender() {
    return sender;
  }

  public EmailTemplate findTemplate(String templateKey) {
    return templates.getOrDefault(templateKey, new EmailTemplate());
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
