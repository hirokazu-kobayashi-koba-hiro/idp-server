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
import com.webauthn4j.util.ArrayUtil;
import com.webauthn4j.util.AssertUtil;
import com.webauthn4j.util.Base64UrlUtil;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.idp.server.authentication.interactors.fido2.Fido2Challenge;

public class WebAuthn4jChallenge implements Challenge {
  byte[] value;

  public WebAuthn4jChallenge(byte[] value) {
    AssertUtil.notNull(value, "value cannot be null");
    this.value = value;
  }

  public WebAuthn4jChallenge(String base64urlString) {
    AssertUtil.notNull(base64urlString, "base64urlString cannot be null");
    this.value = Base64UrlUtil.decode(base64urlString);
  }

  public static WebAuthn4jChallenge generate() {
    byte[] value = new byte[32];
    new SecureRandom().nextBytes(value);
    return new WebAuthn4jChallenge(value);
  }

  public byte[] getValue() {
    return ArrayUtil.clone(this.value);
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o != null && this.getClass() == o.getClass()) {
      WebAuthn4jChallenge that = (WebAuthn4jChallenge) o;
      return Arrays.equals(this.value, that.value);
    } else {
      return false;
    }
  }

  public int hashCode() {
    return Arrays.hashCode(this.value);
  }

  public String toString() {
    return ArrayUtil.toHexString(this.value);
  }

  public String challengeAsString() {

    return Base64.getUrlEncoder().encodeToString(value);
  }

  public Fido2Challenge toWebAuthnChallenge() {
    return new Fido2Challenge(challengeAsString());
  }
}
