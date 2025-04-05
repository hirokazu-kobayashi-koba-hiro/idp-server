package org.idp.server.core.mfa.email;

import org.idp.server.core.basic.json.JsonReadable;

public class EmailMfaConfiguration implements JsonReadable {
  String sender;
  String subject;
  String bodyTemplate;

  public EmailMfaConfiguration() {}

  public EmailMfaConfiguration(String sender, String subject, String bodyTemplate) {
    this.sender = sender;
    this.subject = subject;
    this.bodyTemplate = bodyTemplate;
  }

  public String sender() {
    return sender;
  }

  public String subject() {
    return subject;
  }

  public String bodyTemplate() {
    return bodyTemplate;
  }

  public String interpolateBody(String verificationCode) {
    return bodyTemplate.replace("{VERIFICATION_CODE}", verificationCode);
  }

  public boolean exists() {
    return sender != null && !sender.isEmpty();
  }
}
