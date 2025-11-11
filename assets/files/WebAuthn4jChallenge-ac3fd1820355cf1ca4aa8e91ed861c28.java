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

package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.util.AssertUtil;
import com.webauthn4j.util.Base64UrlUtil;
import java.security.SecureRandom;
import java.util.Objects;
import org.idp.server.platform.json.JsonReadable;

public class WebAuthn4jChallenge implements Challenge, JsonReadable {
  String value;

  public WebAuthn4jChallenge() {}

  public WebAuthn4jChallenge(String base64urlString) {
    AssertUtil.notNull(base64urlString, "base64urlString cannot be null");
    this.value = base64urlString;
  }

  public static WebAuthn4jChallenge generate() {
    byte[] value = new byte[32];
    new SecureRandom().nextBytes(value);
    String encodeValue = Base64UrlUtil.encodeToString(value);
    return new WebAuthn4jChallenge(encodeValue);
  }

  public byte[] getValue() {
    return Base64UrlUtil.decode(value);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    WebAuthn4jChallenge that = (WebAuthn4jChallenge) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public String challengeAsString() {
    // WebAuthn specification requires Base64URL encoding without padding
    return value;
  }
}
