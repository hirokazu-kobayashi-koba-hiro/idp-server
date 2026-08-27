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

package org.idp.server.account_linking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.idp.server.platform.random.RandomStringGenerator;

/**
 * PKCE pair for one linking flow.
 *
 * <p>Introduced here rather than reused from the existing federation path, which has no PKCE at all
 * ({@code OidcTokenRequest} carries no {@code code_verifier}). The linking callback lands on an
 * idp-server URL shared by every RP, so the authorization code appears in this server's access log
 * and in the browser history; binding it to a verifier keeps an intercepted code from being
 * redeemed.
 */
public class AccountLinkingPkce {

  static final String CODE_CHALLENGE_METHOD = "S256";

  String codeVerifier;

  public AccountLinkingPkce(String codeVerifier) {
    this.codeVerifier = codeVerifier;
  }

  public static AccountLinkingPkce generate() {
    return new AccountLinkingPkce(new RandomStringGenerator(32).generate());
  }

  public String codeVerifier() {
    return codeVerifier;
  }

  public String codeChallenge() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for PKCE", e);
    }
  }

  public String codeChallengeMethod() {
    return CODE_CHALLENGE_METHOD;
  }
}
