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

package org.idp.server.core.openid.oauth.type.ciba;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Represents an authentication request identifier for CIBA (Client Initiated Backchannel
 * Authentication).
 *
 * <p>Per OpenID Connect CIBA Core 1.0 Section 7.3, the auth_req_id MUST have at least 128 bits of
 * entropy and SHOULD have 160 bits or more.
 *
 * @see <a
 *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.7.3">CIBA
 *     Core 1.0 Section 7.3</a>
 */
public class AuthReqId {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int ENTROPY_BYTES = 32; // 256 bits

  String value;

  public AuthReqId() {}

  public AuthReqId(String value) {
    this.value = value;
  }

  /**
   * Generates a new AuthReqId with 160 bits of entropy as recommended by CIBA-7.3.
   *
   * @return a new AuthReqId with cryptographically secure random value
   */
  public static AuthReqId generate() {
    byte[] randomBytes = new byte[ENTROPY_BYTES];
    SECURE_RANDOM.nextBytes(randomBytes);
    String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    return new AuthReqId(encoded);
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
    AuthReqId that = (AuthReqId) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
