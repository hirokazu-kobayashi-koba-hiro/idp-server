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

package org.idp.server.platform.jose;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the Jayway-of-JOSE — the {@code nimbus-jose-jwt} behaviors that idp-server's JWT/JOSE code
 * relies on, so an upgrade that silently changes them fails here rather than in production (#1776).
 *
 * <p>Each test exercises the behavior through the wrapper that actually depends on it (JoseType,
 * JsonWebTokenClaims, JwkParser, JsonWebSignatureVerifier), not Nimbus in isolation, so it doubles
 * as a contract test for that wrapper. The null-valued-claim behavior is pinned separately in
 * {@link JsonWebTokenClaimsTest}.
 */
class NimbusJoseBehaviorTest {

  private static String header(String json) {
    String b64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    // JoseType.parse only decodes the header segment; payload/signature can be placeholders.
    return b64 + ".e30.";
  }

  @Nested
  class AlgorithmDetection {

    // JoseType.parse routes on the header alg: this classification drives which context creator
    // (plain / JWS / JWE) handles a token.

    @Test
    void algNoneIsPlain() throws Exception {
      assertEquals(JoseType.plain, JoseType.parse(header("{\"alg\":\"none\"}")));
    }

    @Test
    void jwsAlgorithmsAreSignature() throws Exception {
      assertEquals(JoseType.signature, JoseType.parse(header("{\"alg\":\"ES256\"}")));
      assertEquals(JoseType.signature, JoseType.parse(header("{\"alg\":\"RS256\"}")));
      assertEquals(JoseType.signature, JoseType.parse(header("{\"alg\":\"HS256\"}")));
    }

    @Test
    void jweAlgorithmsAreEncryption() throws Exception {
      // Nimbus classifies alg as a JWE algorithm only when the header carries enc; a real JWE
      // always does. Without enc, an alg like RSA-OAEP-256 is parsed as a JWSAlgorithm.
      assertEquals(
          JoseType.encryption,
          JoseType.parse(header("{\"alg\":\"RSA-OAEP-256\",\"enc\":\"A256GCM\"}")));
      assertEquals(
          JoseType.encryption, JoseType.parse(header("{\"alg\":\"dir\",\"enc\":\"A256GCM\"}")));
    }

    @Test
    void unknownAlgorithmFallsIntoTheSignatureBranch() throws Exception {
      // Nimbus JWSAlgorithm.parse() mints a JWSAlgorithm for any unregistered name, so an
      // unrecognized alg is routed to the JWS path rather than rejected here. Rejecting an
      // unsupported alg is the job of the downstream verifier, not of this classification.
      assertEquals(JoseType.signature, JoseType.parse(header("{\"alg\":\"NOT_A_REAL_ALG\"}")));
    }

    @Test
    void missingAlgIsRejected() {
      // Header.parseAlgorithm throws when alg is absent — the one fail-closed branch here.
      assertThrows(JoseInvalidException.class, () -> JoseType.parse(header("{\"typ\":\"JWT\"}")));
    }

    @Test
    void everyParsedAlgorithmMapsToOneOfTheThreeTypes() throws Exception {
      // Header.parseAlgorithm returns a JWEAlgorithm when enc is present and a JWSAlgorithm
      // otherwise, so JoseType.parse's trailing "Unexpected algorithm type" throw is unreachable.
      // If a Nimbus upgrade introduces a third Algorithm subtype, this expectation breaks first.
      assertEquals(
          JoseType.plain, JoseType.parse(header("{\"alg\":\"none\",\"enc\":\"A256GCM\"}")));
      assertEquals(JoseType.signature, JoseType.parse(header("{\"alg\":\"RSA-OAEP-256\"}")));
    }
  }

  @Nested
  class SymmetricAlgorithmDetection {

    // JsonWebSignature.isSymmetricType() gates the DPoP "alg must not be symmetric" rejection; it
    // reads the parsed header alg and must classify HS256/384/512 as symmetric, asymmetric as not.

    @Test
    void hs256IsSymmetric() throws Exception {
      byte[] secret = new byte[32];
      SignedJWT jwt =
          new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.parse("{\"sub\":\"s1\"}"));
      jwt.sign(new MACSigner(secret));
      JsonWebSignature jws = JsonWebSignature.parse(jwt.serialize());

      assertTrue(jws.isSymmetricType());
    }

    @Test
    void es256IsNotSymmetric() throws Exception {
      String jwk =
          new ECKeyGenerator(Curve.P_256)
              .keyID("k1")
              .algorithm(JWSAlgorithm.ES256)
              .generate()
              .toJSONString();
      JsonWebSignature jws =
          new JsonWebSignatureFactory().createWithAsymmetricKey(Map.of("sub", "s1"), Map.of(), jwk);

      assertFalse(JsonWebSignature.parse(jws.serialize()).isSymmetricType());
    }
  }

  @Nested
  class NumericDateClaims {

    // JWT exp/iat/nbf are epoch SECONDS; Nimbus exposes them as java.util.Date (millis). Every
    // expiry / clock-skew check depends on this unit conversion.

    @Test
    void registeredTimeClaimsAreEpochSeconds() throws ParseException {
      JsonWebTokenClaims claims =
          new JsonWebTokenClaims(
              JWTClaimsSet.parse("{\"exp\":1700000000,\"iat\":1699999999,\"nbf\":1699999998}"));

      assertEquals(1700000000000L, claims.getExp().getTime());
      assertEquals(1699999999000L, claims.getIat().getTime());
      assertEquals(1699999998000L, claims.getNbf().getTime());
    }
  }

  @Nested
  class AudienceCoercion {

    // The aud claim may be a single string or an array; callers always read it as a List and call
    // contains(). Nimbus normalizes both shapes to List<String>.

    @Test
    void singleStringAudienceBecomesSingletonList() throws ParseException {
      JsonWebTokenClaims claims =
          new JsonWebTokenClaims(JWTClaimsSet.parse("{\"aud\":\"solo-client\"}"));

      assertEquals(List.of("solo-client"), claims.getAud());
    }

    @Test
    void arrayAudienceIsPreserved() throws ParseException {
      JsonWebTokenClaims claims =
          new JsonWebTokenClaims(JWTClaimsSet.parse("{\"aud\":[\"a\",\"b\"]}"));

      assertEquals(List.of("a", "b"), claims.getAud());
    }
  }

  @Nested
  class PublicKeyExtraction {

    // DPoP check 7 (jwk must not contain a private key) and public key extraction depend on
    // toPublicJwk() stripping private material.

    @Test
    void toPublicJwkStripsPrivateMaterial() throws Exception {
      ECKey ecKey =
          new ECKeyGenerator(Curve.P_256).keyID("k1").algorithm(JWSAlgorithm.ES256).generate();
      JsonWebKey privateJwk = JwkParser.parse(ecKey.toJSONString());

      assertTrue(privateJwk.isPrivate());
      assertFalse(privateJwk.toPublicJwk().isPrivate());
    }
  }

  @Nested
  class SignatureVerification {

    // The whole system trusts that a valid signature verifies and a tampered one does not.

    private final JsonWebSignatureFactory factory = new JsonWebSignatureFactory();

    private String es256Jwk() throws Exception {
      return new ECKeyGenerator(Curve.P_256)
          .keyID("k1")
          .algorithm(JWSAlgorithm.ES256)
          .generate()
          .toJSONString();
    }

    @Test
    void validSignatureVerifies() throws Exception {
      String jwk = es256Jwk();
      JsonWebSignature jws = factory.createWithAsymmetricKey(Map.of("sub", "s1"), Map.of(), jwk);
      JsonWebKey publicKey = JwkParser.parse(jwk).toPublicJwk();

      JsonWebSignature parsed = JsonWebSignature.parse(jws.serialize());
      JsonWebSignatureVerifier verifier =
          new JsonWebSignatureVerifier(parsed.header(), publicKey.toPublicKey());

      assertDoesNotThrow(() -> verifier.verify(parsed));
    }

    @Test
    void tamperedSignatureFails() throws Exception {
      String jwk = es256Jwk();
      JsonWebSignature jws = factory.createWithAsymmetricKey(Map.of("sub", "s1"), Map.of(), jwk);
      JsonWebKey publicKey = JwkParser.parse(jwk).toPublicJwk();

      // Flip a character in the payload segment.
      String[] parts = jws.serialize().split("\\.");
      char c = parts[1].charAt(0);
      parts[1] = (c == 'A' ? 'B' : 'A') + parts[1].substring(1);
      JsonWebSignature tampered = JsonWebSignature.parse(String.join(".", parts));
      JsonWebSignatureVerifier verifier =
          new JsonWebSignatureVerifier(tampered.header(), publicKey.toPublicKey());

      assertThrows(JoseInvalidException.class, () -> verifier.verify(tampered));
    }
  }
}
