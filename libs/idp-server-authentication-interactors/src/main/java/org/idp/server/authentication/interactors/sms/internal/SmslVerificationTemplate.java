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

package org.idp.server.authentication.interactors.sms.internal;

import org.idp.server.platform.json.JsonReadable;

public class SmslVerificationTemplate implements JsonReadable {

  String subject;
  String body;

  public SmslVerificationTemplate() {}

  public SmslVerificationTemplate(String subject, String body) {
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
