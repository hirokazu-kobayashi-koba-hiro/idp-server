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

package org.idp.server.core.openid.session.logout;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class SessionStateService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int SALT_LENGTH = 16;

  public String calculateSessionState(
      String tenantId, String clientId, String origin, String browserState) {
    String salt = generateSalt();
    String input = tenantId + " " + clientId + " " + origin + " " + browserState + " " + salt;
    String hash = sha256Hex(input);
    return hash + "." + salt;
  }

  public boolean verifySessionState(
      String tenantId, String clientId, String origin, String browserState, String sessionState) {
    if (sessionState == null || !sessionState.contains(".")) {
      return false;
    }

    String[] parts = sessionState.split("\\.", 2);
    if (parts.length != 2) {
      return false;
    }

    String expectedHash = parts[0];
    String salt = parts[1];

    String input = tenantId + " " + clientId + " " + origin + " " + browserState + " " + salt;
    String actualHash = sha256Hex(input);

    return expectedHash.equals(actualHash);
  }

  private String generateSalt() {
    byte[] saltBytes = new byte[SALT_LENGTH];
    SECURE_RANDOM.nextBytes(saltBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
