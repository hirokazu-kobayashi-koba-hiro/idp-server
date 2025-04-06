package org.idp.server.core.authentication.email;

import org.idp.server.core.basic.json.JsonReadable;

public class EmailTemplate implements JsonReadable {

  String subject;
  String body;

  public EmailTemplate() {}

  public EmailTemplate(String subject, String body) {
    this.subject = subject;
    this.body = body;
  }

  public String subject() {
    return subject;
  }

  public String body() {
    return body;
  }

  public String interpolateBody(String verificationCode, int expireSeconds) {
    return body.replace("{VERIFICATION_CODE}", verificationCode)
        .replace("{EXPIRE_SECONDS}", String.valueOf(expireSeconds));
  }

  public boolean exists() {
    return subject != null && body != null;
  }
}
