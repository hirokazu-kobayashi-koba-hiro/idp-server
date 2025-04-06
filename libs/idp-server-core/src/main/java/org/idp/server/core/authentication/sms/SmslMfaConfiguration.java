package org.idp.server.core.authentication.sms;

import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;

public class SmslMfaConfiguration implements JsonReadable {
  String sender;
  Map<String, SmsTemplate> templates;
  int retryCountLimitation;
  int expireSeconds;

  public SmslMfaConfiguration() {}

  public SmslMfaConfiguration(
      String sender,
      Map<String, SmsTemplate> templates,
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

  public SmsTemplate findTemplate(String templateKey) {
    return templates.getOrDefault(templateKey, new SmsTemplate());
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
