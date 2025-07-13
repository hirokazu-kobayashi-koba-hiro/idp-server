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

package org.idp.server.core.oidc.type;

import org.idp.server.platform.exception.UnSupportedException;

public enum AuthFlow {
  OAUTH("oauth"),
  CIBA("ciba"),
  FIDO_UAF_REGISTRATION("fido-uaf-registration"),
  FIDO_UAF_DEREGISTRATION("fido-uaf-deregistration"),
  WEBAUTHN_REGISTRATION("webauthn-registration"),
  WEBAUTHN_DEREGISTRATION("webauthn-deregistration"),
  MFA_SMS_REGISTRATION("mfa-sms-registration"),
  MFA_EMAIL_REGISTRATION("mfa-email-registration");

  String value;

  AuthFlow(String value) {
    this.value = value;
  }

  public static AuthFlow of(String flow) {
    for (AuthFlow authFlow : AuthFlow.values()) {
      if (authFlow.value.equals(flow)) {
        return authFlow;
      }
    }
    throw new UnSupportedException(String.format("unsupported auth flow (%s)", flow));
  }

  public String value() {
    return value;
  }
  ;
}
