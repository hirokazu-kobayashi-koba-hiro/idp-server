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

package org.idp.server.authentication.interactors.sms.executor;

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

  /**
   * Body for a "this was changed" notice sent to the value being replaced (Issue #1416).
   *
   * <p>A separate interpolation because a notice has no code and no expiry: reusing {@link
   * #interpolateBody} would leave {@code {VERIFICATION_CODE\}} unreplaced in the delivered text.
   *
   * @param changedAt when the change was committed
   * @param newValueMasked the new value, partially masked — enough for the recipient to tell
   *     whether they recognise it, without handing a full address to whoever reads the old inbox
   */
  public String interpolateChangeNotice(String changedAt, String newValueMasked) {
    return body.replace("{CHANGED_AT}", changedAt).replace("{NEW_VALUE_MASKED}", newValueMasked);
  }

  public boolean exists() {
    return subject != null && body != null;
  }
}
