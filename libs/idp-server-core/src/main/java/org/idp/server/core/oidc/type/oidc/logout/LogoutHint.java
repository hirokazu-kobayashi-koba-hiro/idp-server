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

package org.idp.server.core.oidc.type.oidc.logout;

import java.util.Objects;

/**
 * logout_hint OPTIONAL.
 *
 * <p>Hint to the Authorization Server about the End-User that is logging out. The value and meaning
 * of this parameter is left up to the OP's discretion. For instance, the value might contain an
 * email address, phone number, username, or session identifier pertaining to the RP's session with
 * the OP for the End-User. (This parameter is intended to be analogous to the login_hint parameter
 * defined in Section 3.1.2.1 of OpenID Connect Core 1.0 [OpenID.Core] that is used in
 * Authentication Requests; whereas, logout_hint is used in RP-Initiated Logout Requests.)
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout">2.
 *     RP-Initiated Logout</a>
 */
public class LogoutHint {
  String value;

  public LogoutHint() {}

  public LogoutHint(String value) {
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
    LogoutHint that = (LogoutHint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
