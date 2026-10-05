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

package org.idp.server.core.openid.oauth.dpop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.idp.server.core.openid.oauth.replay.JwtReplayDetector;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/** Issue #1893: a DPoP proof is accepted once, within its iat window (RFC 9449 Section 4.3). */
class DPoPProofReplayTest {

  static final String HTU = "https://server.example.com/token";
  static final Tenant TENANT =
      new Tenant(
          new TenantIdentifier(UUID.randomUUID().toString()),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          true);

  /** Holds keys like Redis SET NX does. */
  static class NxCacheStore implements CacheStore {
    Map<String, Integer> keys = new HashMap<>();

    @Override
    public <T> void put(String key, T value) {}

    @Override
    public <T> void put(String key, T value, int timeToLiveSeconds) {}

    @Override
    public <T> Optional<T> find(String key, Class<T> type) {
      return Optional.empty();
    }

    @Override
    public boolean exists(String key) {
      return keys.containsKey(key);
    }

    @Override
    public void delete(String key) {}

    @Override
    public void deleteByPrefix(String prefix) {}

    @Override
    public long increment(String key, int timeToLiveSeconds) {
      return 0;
    }

    @Override
    public boolean putIfAbsent(String key, int timeToLiveSeconds) {
      return keys.putIfAbsent(key, timeToLiveSeconds) == null;
    }
  }

  @Test
  void refusesTheSameProofTheSecondTime() throws Exception {
    DPoPProofVerifier verifier = verifier(Duration.ofSeconds(60));
    DPoPProof proof = proof(new KeyHolder(), "jti-1", Instant.now());

    assertTrue(verifier.verify(proof, "POST", HTU, null).exists());
    DPoPProofInvalidException exception =
        assertThrows(
            DPoPProofInvalidException.class, () -> verifier.verify(proof, "POST", HTU, null));
    assertTrue(exception.getMessage().contains("already been used"), exception.getMessage());
  }

  @Test
  void acceptsANewJtiFromTheSameKey() throws Exception {
    DPoPProofVerifier verifier = verifier(Duration.ofSeconds(60));
    KeyHolder key = new KeyHolder();

    verifier.verify(proof(key, "jti-1", Instant.now()), "POST", HTU, null);
    assertTrue(verifier.verify(proof(key, "jti-2", Instant.now()), "POST", HTU, null).exists());
  }

  @Test
  void doesNotUseUpTheJtiOfAProofThatFailsAnotherCheck() throws Exception {
    DPoPProofVerifier verifier = verifier(Duration.ofSeconds(60));
    DPoPProof proof = proof(new KeyHolder(), "jti-1", Instant.now());

    // Wrong htm: refused before the jti is recorded.
    assertThrows(DPoPProofInvalidException.class, () -> verifier.verify(proof, "GET", HTU, null));
    assertTrue(verifier.verify(proof, "POST", HTU, null).exists());
  }

  @Test
  void refusesAnIatOutsideTheWindowAndAcceptsItInAWiderOne() throws Exception {
    DPoPProof old = proof(new KeyHolder(), "jti-1", Instant.now().minusSeconds(90));

    assertThrows(
        DPoPProofInvalidException.class,
        () -> verifier(Duration.ofSeconds(60)).verify(old, "POST", HTU, null));
    assertTrue(verifier(Duration.ofSeconds(120)).verify(old, "POST", HTU, null).exists());
  }

  @Test
  void acceptsTheSameProofAgainWithoutADetector() throws Exception {
    DPoPProofVerifier verifier = new DPoPProofVerifier();
    DPoPProof proof = proof(new KeyHolder(), "jti-1", Instant.now());

    verifier.verify(proof, "POST", HTU, null);
    assertTrue(verifier.verify(proof, "POST", HTU, null).exists());
  }

  private static DPoPProofVerifier verifier(Duration window) {
    return new DPoPProofVerifier(TENANT, window, new JwtReplayDetector(new NxCacheStore()));
  }

  static class KeyHolder {
    KeyPair keyPair;

    KeyHolder() throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      this.keyPair = generator.generateKeyPair();
    }
  }

  private static DPoPProof proof(KeyHolder key, String jti, Instant iat) throws Exception {
    ECPublicKey publicKey = (ECPublicKey) key.keyPair.getPublic();
    String header =
        "{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\""
            + coordinate(publicKey.getW().getAffineX().toByteArray())
            + "\",\"y\":\""
            + coordinate(publicKey.getW().getAffineY().toByteArray())
            + "\"}}";
    String payload =
        "{\"jti\":\""
            + jti
            + "\",\"htm\":\"POST\",\"htu\":\""
            + HTU
            + "\",\"iat\":"
            + iat.getEpochSecond()
            + "}";
    String signingInput =
        b64(header.getBytes(StandardCharsets.UTF_8))
            + "."
            + b64(payload.getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
    signer.initSign(key.keyPair.getPrivate());
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
    return new DPoPProof(signingInput + "." + b64(signer.sign()));
  }

  private static String b64(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private static String coordinate(byte[] twosComplement) {
    byte[] unsigned = new byte[32];
    int length = Math.min(twosComplement.length, 32);
    System.arraycopy(twosComplement, twosComplement.length - length, unsigned, 32 - length, length);
    return b64(unsigned);
  }
}
