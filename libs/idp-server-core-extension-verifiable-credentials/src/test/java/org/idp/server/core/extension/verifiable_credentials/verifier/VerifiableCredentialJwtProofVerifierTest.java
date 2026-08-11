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

package org.idp.server.core.extension.verifiable_credentials.verifier;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.util.List;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.openid.oauth.type.verifiablecredential.CNonce;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.OAuthTokenBuilder;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the OID4VCI JWT proof payload/header checks of {@link
 * VerifiableCredentialJwtProofVerifier}.
 *
 * <p>Three guards were inverted and rejected every well-formed proof: {@code iat} and {@code nonce}
 * were missing a {@code !}, and the third disjunct of the multi-key check read {@code hasJwk() &&
 * (hasKid() || hasJwk())}, which collapses to {@code hasJwk()} and so rejected any jwk-only proof.
 * The existing e2e coverage only exercises malformed requests that fail before this verifier runs,
 * which is how all three survived (#1779).
 */
class VerifiableCredentialJwtProofVerifierTest {

  private static VerifiableCredentialJwtProofVerifier verifierWithCNonce(String cNonce) {
    OAuthToken oAuthToken =
        new OAuthTokenBuilder(new OAuthTokenIdentifier("token-1")).add(new CNonce(cNonce)).build();
    return new VerifiableCredentialJwtProofVerifier("jwt", oAuthToken, null, null);
  }

  private static VerifiableCredentialJwtProofVerifier verifier() {
    return new VerifiableCredentialJwtProofVerifier("jwt", null, null, null);
  }

  private static JsonWebTokenClaims claims(String json) throws ParseException {
    return new JsonWebTokenClaims(JWTClaimsSet.parse(json));
  }

  private static JsonWebSignatureHeader header(JWSHeader.Builder builder) {
    return new JsonWebSignatureHeader(builder.build());
  }

  private static JWSHeader.Builder builder() {
    return new JWSHeader.Builder(JWSAlgorithm.ES256);
  }

  private static com.nimbusds.jose.jwk.JWK publicJwk() throws Exception {
    return new ECKeyGenerator(Curve.P_256).keyID("k1").generate().toPublicJWK();
  }

  @Nested
  class Iat {

    @Test
    void proofCarryingIatPasses() throws ParseException {
      assertDoesNotThrow(
          () -> verifier().throwExceptionIfInvalidIat(claims("{\"iat\":1700000000}")));
    }

    @Test
    void proofWithoutIatIsRejected() throws ParseException {
      VerifiableCredentialBadRequestException exception =
          assertThrows(
              VerifiableCredentialBadRequestException.class,
              () -> verifier().throwExceptionIfInvalidIat(claims("{\"sub\":\"s1\"}")));

      assertTrue(exception.getMessage().contains("iat is required"));
    }

    @Test
    void nullValuedIatIsRejected() throws ParseException {
      // hasIat() is value-based since #1776, so a null-valued iat counts as absent.
      assertThrows(
          VerifiableCredentialBadRequestException.class,
          () -> verifier().throwExceptionIfInvalidIat(claims("{\"iat\":null}")));
    }
  }

  @Nested
  class Nonce {

    @Test
    void nonceMatchingCNoncePasses() throws ParseException {
      assertDoesNotThrow(
          () ->
              verifierWithCNonce("c-nonce-1")
                  .throwExceptionIfInvalidNonce(claims("{\"nonce\":\"c-nonce-1\"}")));
    }

    @Test
    void proofWithoutNonceIsRejected() throws ParseException {
      VerifiableCredentialBadRequestException exception =
          assertThrows(
              VerifiableCredentialBadRequestException.class,
              () ->
                  verifierWithCNonce("c-nonce-1")
                      .throwExceptionIfInvalidNonce(claims("{\"sub\":\"s1\"}")));

      assertTrue(exception.getMessage().contains("nonce is required"));
    }

    @Test
    void nonceNotMatchingCNonceIsRejected() throws ParseException {
      VerifiableCredentialBadRequestException exception =
          assertThrows(
              VerifiableCredentialBadRequestException.class,
              () ->
                  verifierWithCNonce("c-nonce-1")
                      .throwExceptionIfInvalidNonce(claims("{\"nonce\":\"other\"}")));

      assertTrue(exception.getMessage().contains("does not match c_nonce"));
    }

    @Test
    void nonStringNonceIsRejectedWithoutClassCastException() throws ParseException {
      // getValue() absorbs the type mismatch (#1779); the proof is refused, not a 500.
      assertThrows(
          VerifiableCredentialBadRequestException.class,
          () ->
              verifierWithCNonce("c-nonce-1")
                  .throwExceptionIfInvalidNonce(claims("{\"nonce\":123}")));
    }
  }

  @Nested
  class MultiKeyClaims {

    @Test
    void kidOnlyPasses() {
      assertDoesNotThrow(
          () -> verifier().throwExceptionIfMultiKeyClaims(header(builder().keyID("k1"))));
    }

    @Test
    void jwkOnlyPasses() throws Exception {
      assertDoesNotThrow(
          () -> verifier().throwExceptionIfMultiKeyClaims(header(builder().jwk(publicJwk()))));
    }

    @Test
    void x5cOnlyPasses() {
      assertDoesNotThrow(
          () ->
              verifier()
                  .throwExceptionIfMultiKeyClaims(
                      header(builder().x509CertChain(List.of(new Base64("cert"))))));
    }

    @Test
    void kidAndJwkIsRejected() throws Exception {
      assertThrows(
          VerifiableCredentialBadRequestException.class,
          () ->
              verifier()
                  .throwExceptionIfMultiKeyClaims(header(builder().keyID("k1").jwk(publicJwk()))));
    }

    @Test
    void kidAndX5cIsRejected() {
      assertThrows(
          VerifiableCredentialBadRequestException.class,
          () ->
              verifier()
                  .throwExceptionIfMultiKeyClaims(
                      header(builder().keyID("k1").x509CertChain(List.of(new Base64("cert"))))));
    }

    @Test
    void jwkAndX5cIsRejected() throws Exception {
      assertThrows(
          VerifiableCredentialBadRequestException.class,
          () ->
              verifier()
                  .throwExceptionIfMultiKeyClaims(
                      header(
                          builder().jwk(publicJwk()).x509CertChain(List.of(new Base64("cert"))))));
    }
  }
}
