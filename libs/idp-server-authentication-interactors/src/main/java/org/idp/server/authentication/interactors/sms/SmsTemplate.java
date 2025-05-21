package org.idp.server.authentication.interactors.sms;

import org.idp.server.basic.json.JsonReadable;

public class SmsTemplate implements JsonReadable {

  String message;

  public SmsTemplate() {}

  public SmsTemplate(String message) {
    this.message = message;
  }

  public String message() {
    return message;
  }

  public String interpolate(String verificationCode, int expireSeconds) {
    return message
        .replace("{VERIFICATION_CODE}", verificationCode)
        .replace("{EXPIRE_SECONDS}", String.valueOf(expireSeconds));
  }

  public boolean exists() {
    return message != null;
  }
}
