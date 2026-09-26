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
package org.idp.server.core.extension.oid4vci.nonce;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.idp.server.platform.date.SystemDateTime;

/**
 * Issues {@code c_nonce} values: 32 random bytes, base64url without padding.
 *
 * <p>Section 7.2: "New challenge values MUST be unpredictable." The value carries no structure; it
 * is looked up server-side.
 */
public class CredentialNonceIssuer {

  static final int NONCE_BYTES = 32;

  SecureRandom secureRandom = new SecureRandom();

  public CredentialNonce issue(int expiresInSeconds) {
    byte[] random = new byte[NONCE_BYTES];
    secureRandom.nextBytes(random);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

    LocalDateTime now = SystemDateTime.now();
    return new CredentialNonce(nonce, now.plusSeconds(expiresInSeconds), now);
  }
}
