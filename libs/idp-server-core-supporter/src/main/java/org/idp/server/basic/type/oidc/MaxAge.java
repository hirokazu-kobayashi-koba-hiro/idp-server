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
 * MaxAge
 *
 * <p>OPTIONAL. Maximum Authentication Age.
 *
 * <p>Specifies the allowable elapsed time in seconds since the last time the End-User was actively
 * authenticated by the OP. If the elapsed time is greater than this value, the OP MUST attempt to
 * actively re-authenticate the End-User.
 *
 * <p>(The max_age request parameter corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] max_auth_age
 * request parameter.) When max_age is used, the ID Token returned MUST include an auth_time Claim
 * Value.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public class MaxAge {
  String value;

  public MaxAge() {}

  public MaxAge(String value) {
    this.value = value;
  }

  public MaxAge(long value) {
    this.value = String.valueOf(value);
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public long toLongValue() {
    return Long.parseLong(value);
  }

  public boolean isValid() {
    if (!exists()) {
      return true;
    }
    try {
      long longValue = toLongValue();
      return longValue > 0;
    } catch (Exception e) {
      return false;
    }
  }
}
