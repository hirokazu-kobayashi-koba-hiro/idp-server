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
 * Secret handed to the browser at {@code /linking/start}, so the callback can tell it is the same
 * browser coming back.
 *
 * <p>The callback arrives from the external IdP as a plain navigation with no Bearer token, so on
 * its own it cannot establish whose link it is. Without this binding an attacker walks {@code
 * /linking/start} himself — he is the user the session is bound to, so the operator check there
 * passes for him — copies the external authorization URL out of the redirect, and hands it to a
 * victim. The victim's tokens then arrive at the callback carrying the attacker's {@code state}.
 *
 * <p>Only the hash is stored. The secret itself lives in a cookie, so a read of the session table
 * does not yield something that can complete a link.
 */
public class AccountLinkingBrowserBinding {

  String secret;

  AccountLinkingBrowserBinding(String secret) {
    this.secret = secret;
  }

  public static AccountLinkingBrowserBinding generate() {
    return new AccountLinkingBrowserBinding(new RandomStringGenerator(32).generate());
  }

  public String secret() {
    return secret;
  }

  public String hash() {
    return hashOf(secret);
  }

  public static String hashOf(String secret) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required for account linking browser binding", e);
    }
  }

  /** Constant-time comparison of a presented secret against a stored hash. */
  public static boolean matches(String storedHash, String presentedSecret) {
    if (storedHash == null || presentedSecret == null || presentedSecret.isEmpty()) {
      return false;
    }
    return MessageDigest.isEqual(
        storedHash.getBytes(StandardCharsets.UTF_8),
        hashOf(presentedSecret).getBytes(StandardCharsets.UTF_8));
  }
}
