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
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DPoPProofVerifier#verifyFapiSigningAlgorithm}, the FAPI 2.0 §5.4 DPoP proof
 * signing algorithm restriction. It only inspects the JWS {@code alg} header, so a JWS created with
 * a throwaway key (whose signature would not verify) is sufficient to exercise it.
 */
class DPoPProofVerifierTest {

  // Throwaway RSA key (RS256) — only used so the factory emits an RS256-headed JWS. The signature
  // is never verified in verifyAlgorithm.
  private static final String RSA_JWK =
      """
      {
        "kty": "RSA",
        "kid": "test-rsa-key",
        "use": "sig",
        "alg": "RS256",
        "n": "xGOr-H7A-PWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOZWRSi6S5xTOWZWRSi6S5xTOWZxrR0k7vOQ",
        "e": "AQAB",
        "d": "GmiaucgzmRpjCpL3YilHUHQiLbKgRSqHKmCJqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCZqPPjkJqiKDMJi3cV7kCQ"
      }
      """;

  // Throwaway EC key (ES256).
  private static final String EC_JWK =
      """
      {
        "kty": "EC",
        "kid": "test-ec-key",
        "use": "sig",
        "alg": "ES256",
        "crv": "P-256",
        "x": "WKn-ZIGevcwGIyyrzFoZNBdaq9_TsqzGl96oc0CWuis",
        "y": "y77t-RvAHRKTsSGdIYUfweuOvwrvDD-Q3Hv5J0fSKbE",
        "d": "Ak5Z5Tm4VWsLhDBXVKX8J4H4mfqGqLKqqLvpqvqG4q0"
      }
      """;

  private final JsonWebSignatureFactory factory = new JsonWebSignatureFactory();
  private final DPoPProofVerifier verifier = new DPoPProofVerifier();

  private DPoPProof dpopProof(String jwk) throws Exception {
    Map<String, Object> claims = new HashMap<>();
    claims.put("jti", "test-jti");
    JsonWebSignature jws = factory.createWithAsymmetricKey(claims, new HashMap<>(), jwk);
    return new DPoPProof(jws.serialize());
  }

  @Test
  void fapiSigningAlgorithmRejectsRs256() throws Exception {
    DPoPProof rs256 = dpopProof(RSA_JWK);

    DPoPProofInvalidException ex =
        assertThrows(
            DPoPProofInvalidException.class, () -> verifier.verifyFapiSigningAlgorithm(rs256));
    assertTrue(ex.getMessage().contains("FAPI 2.0"), ex.getMessage());
  }

  @Test
  void rejectsAProofWhosePayloadIsNotAClaimsSetAsAnInvalidProof() throws Exception {
    // Signed with the key the proof carries, so the signature verifies; only the payload is not a
    // claims set. Anyone can build one, and it is a malformed proof rather than an internal error.
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair keyPair = generator.generateKeyPair();
    ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();

    String header =
        "{\"typ\":\"dpop+jwt\",\"alg\":\"ES256\",\"jwk\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\""
            + coordinate(publicKey.getW().getAffineX().toByteArray())
            + "\",\"y\":\""
            + coordinate(publicKey.getW().getAffineY().toByteArray())
            + "\"}}";
    String signingInput =
        b64(header.getBytes(StandardCharsets.UTF_8))
            + "."
            + b64("not a json object".getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
    signer.initSign(keyPair.getPrivate());
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
    String proof = signingInput + "." + b64(signer.sign());

    DPoPProofInvalidException exception =
        assertThrows(
            DPoPProofInvalidException.class,
            () ->
                verifier.verify(
                    new DPoPProof(proof), "POST", "https://server.example.com/token", null));
    assertTrue(exception.getMessage().contains("not a claims set"), exception.getMessage());
  }

  private static String b64(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  /** A P-256 coordinate as JWK wants it: unsigned, 32 bytes. */
  private static String coordinate(byte[] twosComplement) {
    byte[] unsigned = new byte[32];
    int length = Math.min(twosComplement.length, 32);
    System.arraycopy(twosComplement, twosComplement.length - length, unsigned, 32 - length, length);
    return b64(unsigned);
  }

  @Test
  void fapiSigningAlgorithmAllowsEs256() throws Exception {
    DPoPProof es256 = dpopProof(EC_JWK);

    // ES256 is in the FAPI 2.0 §5.4 set.
    assertDoesNotThrow(() -> verifier.verifyFapiSigningAlgorithm(es256));
  }
}
