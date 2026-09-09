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

package org.idp.server.core.openid.identity.email;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonNodeWrapper;

/** Request body of the self-service email endpoints (Issue #1416). */
public class EmailVerificationRequest {

  Map<String, Object> values;

  public EmailVerificationRequest() {
    this.values = new HashMap<>();
  }

  public EmailVerificationRequest(Map<String, Object> values) {
    this.values = values == null ? new HashMap<>() : values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public JsonNodeWrapper toJsonNodeWrapper() {
    return JsonNodeWrapper.fromObject(values);
  }

  /**
   * Reads {@code new_email} as a string.
   *
   * <p>Only meaningful for a change; the verify operation never consults the body. Type checking is
   * the validator's job — this returns empty for a non-string so a coerced value can never reach
   * the sender.
   */
  public String newEmail() {
    Object value = values.get("new_email");
    if (value instanceof String string) {
      return string;
    }
    return "";
  }

  public String verificationCode() {
    Object value = values.get("verification_code");
    if (value instanceof String string) {
      return string;
    }
    return "";
  }
}
