/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.email;

import org.idp.server.basic.json.JsonReadable;

public class EmailVerificationTemplate implements JsonReadable {

  String subject;
  String body;

  public EmailVerificationTemplate() {}

  public EmailVerificationTemplate(String subject, String body) {
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
