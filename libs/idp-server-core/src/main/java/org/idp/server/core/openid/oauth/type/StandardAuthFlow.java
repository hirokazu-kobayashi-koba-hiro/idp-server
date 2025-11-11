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

package org.idp.server.core.openid.oauth.type;

import org.idp.server.platform.exception.UnSupportedException;

public enum StandardAuthFlow {
  OAUTH("oauth"),
  CIBA("ciba"),
  FIDO_UAF_REGISTRATION("fido-uaf-registration"),
  FIDO_UAF_DEREGISTRATION("fido-uaf-deregistration"),
  FIDO2_REGISTRATION("fido2-registration"),
  FIDO2_DEREGISTRATION("fido2-deregistration"),
  MFA_SMS_REGISTRATION("mfa-sms-registration"),
  MFA_EMAIL_REGISTRATION("mfa-email-registration");

  String value;

  StandardAuthFlow(String value) {
    this.value = value;
  }

  public static StandardAuthFlow of(String flow) {
    for (StandardAuthFlow standardAuthFlow : StandardAuthFlow.values()) {
      if (standardAuthFlow.value.equals(flow)) {
        return standardAuthFlow;
      }
    }
    throw new UnSupportedException(String.format("unsupported auth flow (%s)", flow));
  }

  public String value() {
    return value;
  }

  public AuthFlow toAuthFlow() {
    return new AuthFlow(this.value);
  }
}
