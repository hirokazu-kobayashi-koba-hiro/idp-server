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

package org.idp.server.core.openid.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * SessionHashCalculator
 *
 * <p>Utility for computing session hashes for OIDC Session Management. Similar to Keycloak's
 * session cookie hash computation.
 */
public class SessionHashCalculator {

  private SessionHashCalculator() {}

  /**
   * Computes SHA256 hash of input and returns URL-safe Base64 encoded result.
   *
   * @param input the input string
   * @return URL-safe Base64 encoded SHA256 hash
   */
  public static String sha256UrlEncodedHash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  /**
   * Verifies if the provided hash matches the expected hash for the session ID.
   *
   * @param opSessionId the OP session ID
   * @param providedHash the hash to verify
   * @return true if hashes match
   */
  public static boolean verifySessionHash(String opSessionId, String providedHash) {
    if (opSessionId == null || providedHash == null) {
      return false;
    }
    String expectedHash = sha256UrlEncodedHash(opSessionId);
    return expectedHash.equals(providedHash);
  }
}
