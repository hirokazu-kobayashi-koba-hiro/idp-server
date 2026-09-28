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

package org.idp.server.platform.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and checks server-provided nonces that need no storage: a value this server hands out and
 * later has to recognize as its own, still fresh, and meant for the same tenant and purpose.
 *
 * <p>The value is {@code base64url(payload) "." base64url(HMAC-SHA256(payload))}. The payload packs
 * a version, the tenant, the expiry and 16 random bytes; the MAC key is derived from the server
 * secret per purpose, so a value minted for one purpose (an attestation challenge, say) does not
 * verify for another (a credential nonce). About 100 characters, opaque to the client.
 *
 * <p>What this proves: issued by this server, for this tenant and purpose, not expired. What it
 * does not: that it was used only once. A purpose that needs single use records the spent values
 * itself.
 */
public class ServerNonceCodec {

  static final byte VERSION = 1;
  static final int RANDOM_BYTES = 16;
  static final int PAYLOAD_BYTES = 1 + 16 + 8 + RANDOM_BYTES;

  static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  String secret;
  SecureRandom secureRandom = new SecureRandom();
  Map<String, HmacHasher> purposeHashers = new ConcurrentHashMap<>();

  public ServerNonceCodec(String secret) {
    if (secret == null || secret.isEmpty()) {
      throw new IllegalArgumentException("server nonce secret is required");
    }
    this.secret = secret;
  }

  /**
   * @param purpose what the nonce is for; part of the MAC key, never of the value
   * @param tenantId the tenant the nonce belongs to (a UUID)
   * @param expiresAtEpochSecond when the nonce stops being accepted
   */
  public String issue(String purpose, String tenantId, long expiresAtEpochSecond) {
    byte[] random = new byte[RANDOM_BYTES];
    secureRandom.nextBytes(random);

    UUID tenant = UUID.fromString(tenantId);
    ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_BYTES);
    payload.put(VERSION);
    payload.putLong(tenant.getMostSignificantBits());
    payload.putLong(tenant.getLeastSignificantBits());
    payload.putLong(expiresAtEpochSecond);
    payload.put(random);

    String encodedPayload = ENCODER.encodeToString(payload.array());
    return encodedPayload + "." + macOf(purpose, encodedPayload);
  }

  /**
   * @return the nonce's expiry, or {@code null} when it is not a value this server issued for this
   *     purpose and tenant (forged, altered, another tenant's, another purpose's, malformed).
   *     Whether it has expired is the caller's comparison, against its own clock.
   */
  public Long verify(String purpose, String tenantId, String value) {
    if (value == null) {
      return null;
    }
    int separator = value.indexOf('.');
    if (separator <= 0 || separator != value.lastIndexOf('.')) {
      return null;
    }
    String encodedPayload = value.substring(0, separator);
    String presentedMac = value.substring(separator + 1);

    byte[] expected = macOf(purpose, encodedPayload).getBytes(StandardCharsets.US_ASCII);
    if (!MessageDigest.isEqual(expected, presentedMac.getBytes(StandardCharsets.US_ASCII))) {
      return null;
    }

    byte[] payload;
    try {
      payload = DECODER.decode(encodedPayload);
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (payload.length != PAYLOAD_BYTES || payload[0] != VERSION) {
      return null;
    }
    ByteBuffer buffer = ByteBuffer.wrap(payload, 1, PAYLOAD_BYTES - 1);
    UUID tenant = new UUID(buffer.getLong(), buffer.getLong());
    if (!tenant.equals(UUID.fromString(tenantId))) {
      return null;
    }
    return buffer.getLong();
  }

  String macOf(String purpose, String encodedPayload) {
    HmacHasher hasher =
        purposeHashers.computeIfAbsent(
            purpose,
            key -> new HmacHasher(new HmacHasher(secret).hash("idp-server/server-nonce/" + key)));
    return hasher.hash(encodedPayload);
  }
}
