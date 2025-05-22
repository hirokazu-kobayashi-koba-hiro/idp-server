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


package org.idp.server.basic.type.oidc;

import java.util.Objects;

/**
 * login_hint OPTIONAL.
 *
 * <p>Hint to the Authorization Server about the login identifier the End-User might use to log in
 * (if necessary). This hint can be used by an RP if it first asks the End-User for their e-mail
 * address (or other identifier) and then wants to pass that value as a hint to the discovered
 * authorization service. It is RECOMMENDED that the hint value match the value used for discovery.
 * This value MAY also be a phone number in the format specified for the phone_number Claim. The use
 * of this parameter is left to the OP's discretion.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class LoginHint {
  String value;

  public LoginHint() {}

  public LoginHint(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LoginHint that = (LoginHint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
