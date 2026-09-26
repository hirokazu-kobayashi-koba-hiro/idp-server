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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRequestHash;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;

/**
 * What the app embeds in the platform attestation to tie it to the registration.
 *
 * <p>Android's {@code attestationChallenge} is "arbitrary bytes", and Apple's {@code
 * clientDataHash} is "the SHA256 hash of the one-time challenge your server sends"; neither says
 * what is meant when the challenge travels as text. Client implementations differ on it, and a
 * mismatch shows only as a nonce that does not match, on an endpoint that deliberately does not say
 * why. So the choice is made explicit per client, from a closed list rather than a template: each
 * value ties the evidence to this registration's challenge, and a free form could be configured
 * into one that does not.
 *
 * <p>With {@code challenge = "Zm9vYmFyLWNoYWxsZW5nZS0wMQ"} and the key of the {@code request_hash}
 * vector ({@code x = VcKVNBZ4IaBAYW3jxM4w3TJFVA7myeUGQyGt-g_yvpQ}, {@code y =
 * f-E-hYE3TAWKwhVv9pej9NABs9SX9XsNO80x57jFTyU}):
 *
 * <pre>
 * value           Android attestationChallenge (hex)                    iOS clientDataHash (hex)
 * challenge       666f6f6261722d6368616c6c656e67652d3031                352c35fa1dac334a252a5c43601c542c2db3e4878f7ea78a0e16010998550cd8
 * challenge_text  5a6d3976596d46794c574e6f595778735a57356e5a5330774d51  06867a128ca08e2c8b7ec015b4c84efb146cd8cb983efa7d239fdbae2728c892
 * request_hash    618fa70c42ba24740b55ef3ddea89e0a2cb2436916e5f06620f36555d7e58f52 (both)
 * </pre>
 */
public enum PlatformChallengeBinding {

  /** The bytes the base64url challenge decodes to. The default. */
  challenge,

  /** The challenge as received, its UTF-8 bytes. */
  challenge_text,

  /**
   * {@code request_hash}: SHA-256 of the challenge bytes and the canonical instance key. Already a
   * SHA-256, so iOS uses it as {@code clientDataHash} without hashing it again.
   */
  request_hash;

  public static final String CONFIG_KEY = "challenge_binding";

  /**
   * Reads {@code challenge_binding} from a platform's settings.
   *
   * @throws PlatformAttestationVerificationException when the value is not one of the list
   */
  public static PlatformChallengeBinding fromSettings(Map<String, Object> settings) {
    Object value = settings.get(CONFIG_KEY);
    if (!(value instanceof String name) || name.isEmpty()) {
      return challenge;
    }
    for (PlatformChallengeBinding binding : values()) {
      if (binding.name().equals(name)) {
        return binding;
      }
    }
    throw new PlatformAttestationVerificationException("unknown " + CONFIG_KEY + ": " + name);
  }

  /** The bytes the app passes to the platform as the challenge (Android's attestationChallenge). */
  public byte[] challengeBytes(String registrationChallenge, Map<String, Object> instanceKey) {
    return switch (this) {
      case challenge -> Base64.getUrlDecoder().decode(registrationChallenge);
      case challenge_text -> registrationChallenge.getBytes(StandardCharsets.UTF_8);
      case request_hash ->
          Base64.getUrlDecoder()
              .decode(ClientInstanceRequestHash.derive(registrationChallenge, instanceKey).value());
    };
  }

  /** Apple's {@code clientDataHash}: the SHA-256 of the challenge bytes, or request_hash itself. */
  public byte[] clientDataHash(String registrationChallenge, Map<String, Object> instanceKey) {
    byte[] bytes = challengeBytes(registrationChallenge, instanceKey);
    if (this == request_hash) {
      return bytes;
    }
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
