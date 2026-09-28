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
package org.idp.server.core.openid.extension.attestation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.junit.jupiter.api.Test;

/**
 * Pins what the app embeds in the platform attestation for each {@code challenge_binding}. These
 * are the values published in the protocol document, so a client implementation can be checked
 * against them without a device.
 */
class PlatformChallengeBindingTest {

  static final String CHALLENGE = "Zm9vYmFyLWNoYWxsZW5nZS0wMQ";

  /** The key of the request_hash fixed vector. */
  static final Map<String, Object> INSTANCE_KEY =
      Map.of(
          "kty", "EC",
          "crv", "P-256",
          "x", "VcKVNBZ4IaBAYW3jxM4w3TJFVA7myeUGQyGt-g_yvpQ",
          "y", "f-E-hYE3TAWKwhVv9pej9NABs9SX9XsNO80x57jFTyU");

  private static String hex(byte[] value) {
    return HexFormat.of().formatHex(value);
  }

  @Test
  void challengeEmbedsTheDecodedBytes() {
    PlatformChallengeBinding binding = PlatformChallengeBinding.challenge;

    assertEquals(
        "666f6f6261722d6368616c6c656e67652d3031",
        hex(binding.challengeBytes(CHALLENGE, INSTANCE_KEY)));
    assertEquals(
        "352c35fa1dac334a252a5c43601c542c2db3e4878f7ea78a0e16010998550cd8",
        hex(binding.clientDataHash(CHALLENGE, INSTANCE_KEY)));
  }

  @Test
  void challengeTextEmbedsTheUtf8OfTheString() {
    PlatformChallengeBinding binding = PlatformChallengeBinding.challenge_text;

    assertEquals(
        "5a6d3976596d46794c574e6f595778735a57356e5a5330774d51",
        hex(binding.challengeBytes(CHALLENGE, INSTANCE_KEY)));
    assertEquals(
        "06867a128ca08e2c8b7ec015b4c84efb146cd8cb983efa7d239fdbae2728c892",
        hex(binding.clientDataHash(CHALLENGE, INSTANCE_KEY)));
  }

  @Test
  void requestHashIsUsedAsIsOnBothPlatforms() {
    PlatformChallengeBinding binding = PlatformChallengeBinding.request_hash;
    String requestHash = "618fa70c42ba24740b55ef3ddea89e0a2cb2436916e5f06620f36555d7e58f52";

    assertEquals(requestHash, hex(binding.challengeBytes(CHALLENGE, INSTANCE_KEY)));
    // Already a SHA-256: iOS takes it as clientDataHash without hashing it again.
    assertEquals(requestHash, hex(binding.clientDataHash(CHALLENGE, INSTANCE_KEY)));
  }

  @Test
  void defaultsToChallenge() {
    assertEquals(
        PlatformChallengeBinding.challenge, PlatformChallengeBinding.fromSettings(Map.of()));
  }

  @Test
  void refusesAValueOutsideTheList() {
    assertThrows(
        PlatformAttestationVerificationException.class,
        () ->
            PlatformChallengeBinding.fromSettings(
                Map.of("challenge_binding", "sha1_of_challenge")));
  }
}
