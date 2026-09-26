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
package org.idp.server.core.extension.oid4vci.verifier;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.idp.server.core.extension.oid4vci.exception.CredentialRequestInvalidException;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialProofTypeConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** OpenID4VCI 1.0 Appendix F.1 / F.4: what makes a jwt key proof acceptable. */
class JwtProofVerifierTest {

  static final String ISSUER = "https://issuer.example.com/tenant";
  static final String CLIENT_ID = "wallet";

  ECKey holderKey;
  CredentialProofTypeConfiguration es256Only;

  @BeforeEach
  void setUp() throws Exception {
    holderKey = new ECKeyGenerator(Curve.P_256).generate();
    es256Only =
        JsonConverter.snakeCaseInstance()
            .read(
                Map.of("proof_signing_alg_values_supported", java.util.List.of("ES256")),
                CredentialProofTypeConfiguration.class);
  }

  private JWSHeader.Builder header() {
    return new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("openid4vci-proof+jwt"))
        .jwk(holderKey.toPublicJWK());
  }

  private JWTClaimsSet.Builder claims() {
    return new JWTClaimsSet.Builder()
        .issuer(CLIENT_ID)
        .audience(ISSUER)
        .issueTime(new Date())
        .claim("nonce", "n-1");
  }

  private String proof(UnaryOperator<JWSHeader.Builder> h, UnaryOperator<JWTClaimsSet.Builder> c)
      throws Exception {
    SignedJWT jwt = new SignedJWT(h.apply(header()).build(), c.apply(claims()).build());
    jwt.sign(new ECDSASigner(holderKey));
    return jwt.serialize();
  }

  private VerifiedJwtProof verify(String proof) {
    return new JwtProofVerifier(proof, es256Only, ISSUER, CLIENT_ID).verify();
  }

  private void assertInvalidProof(String proof) {
    CredentialRequestInvalidException e =
        assertThrows(CredentialRequestInvalidException.class, () -> verify(proof));
    assertEquals("invalid_proof", e.error());
  }

  @Test
  @DisplayName("正しい proof は holder の公開鍵と nonce を返す")
  void acceptsAWellFormedProof() throws Exception {
    VerifiedJwtProof verified = verify(proof(h -> h, c -> c));

    assertEquals("n-1", verified.nonce());
    assertEquals(
        holderKey.toPublicJWK().computeThumbprint().toString(),
        verified.holderKey().thumbprintSha256());
    assertFalse(verified.holderKey().isPrivate());
  }

  @Test
  @DisplayName("typ: REQUIRED. MUST be openid4vci-proof+jwt")
  void rejectsAnotherType() throws Exception {
    assertInvalidProof(proof(h -> h.type(new JOSEObjectType("JWT")), c -> c));
  }

  @Test
  @DisplayName("alg は proof_signing_alg_values_supported のどれかでなければならない")
  void rejectsAnUnsupportedAlgorithm() throws Exception {
    ECKey p384 = new ECKeyGenerator(Curve.P_384).generate();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES384)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .jwk(p384.toPublicJWK())
                .build(),
            claims().build());
    jwt.sign(new ECDSASigner(p384));

    assertInvalidProof(jwt.serialize());
  }

  @Test
  @DisplayName("alg: MUST NOT be none or an identifier for a symmetric algorithm (MAC)")
  void rejectsASymmetricAlgorithm() throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new JOSEObjectType("openid4vci-proof+jwt"))
                .build(),
            claims().build());
    jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));

    assertInvalidProof(jwt.serialize());
  }

  @Test
  @DisplayName("kid / jwk / x5c は排他。束縛する鍵は jwk で受け取る")
  void requiresTheKeyInTheJwkHeader() throws Exception {
    assertInvalidProof(proof(h -> h.jwk(null).keyID("did:example:123#key-1"), c -> c));
    assertInvalidProof(proof(h -> h.keyID("key-1"), c -> c));
  }

  @Test
  @DisplayName("the header parameter does not contain a private key")
  void rejectsAPrivateKeyInTheHeader() throws Exception {
    // nimbus refuses to put a private JWK into a header, so the proof is built by hand.
    String header =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"alg\":\"ES256\",\"typ\":\"openid4vci-proof+jwt\",\"jwk\":"
                        + holderKey.toJSONString()
                        + "}")
                    .getBytes());
    String payload = claims().build().toPayload().toBase64URL().toString();
    String signingInput = header + "." + payload;
    String signature =
        new ECDSASigner(holderKey)
            .sign(new JWSHeader(JWSAlgorithm.ES256), signingInput.getBytes())
            .toString();

    assertInvalidProof(signingInput + "." + signature);
  }

  @Test
  @DisplayName("署名は jwk ヘッダーの鍵で検証できなければならない")
  void rejectsAProofSignedByAnotherKey() throws Exception {
    ECKey other = new ECKeyGenerator(Curve.P_256).generate();
    SignedJWT jwt = new SignedJWT(header().build(), claims().build());
    jwt.sign(new ECDSASigner(other));

    assertInvalidProof(jwt.serialize());
  }

  @Test
  @DisplayName("aud: REQUIRED. The value of this claim MUST be the Credential Issuer Identifier.")
  void requiresTheCredentialIssuerAsAudience() throws Exception {
    assertInvalidProof(proof(h -> h, c -> c.audience("https://another.example.com")));
    assertInvalidProof(proof(h -> h, c -> c.audience((String) null)));
  }

  @Test
  @DisplayName("iss: OPTIONAL. ただしあれば client_id でなければならない")
  void issMustBeTheClientWhenPresent() throws Exception {
    assertEquals("n-1", verify(proof(h -> h, c -> c.issuer(null))).nonce());
    assertInvalidProof(proof(h -> h, c -> c.issuer("another-client")));
  }

  @Test
  @DisplayName("iat: REQUIRED。未来すぎる・古すぎるものは受け付けない")
  void requiresAFreshIat() throws Exception {
    assertInvalidProof(proof(h -> h, c -> c.issueTime(null)));
    assertInvalidProof(
        proof(h -> h, c -> c.issueTime(new Date(System.currentTimeMillis() + 600_000))));
    assertInvalidProof(
        proof(h -> h, c -> c.issueTime(new Date(System.currentTimeMillis() - 600_000))));
  }

  @Test
  @DisplayName("nonce: MUST be present when the issuer has a Nonce Endpoint（無ければ invalid_proof）")
  void requiresANonce() throws Exception {
    assertInvalidProof(proof(h -> h, c -> c.claim("nonce", null)));
  }

  @Test
  @DisplayName("JWT として読めないものは invalid_proof")
  void rejectsGarbage() {
    assertInvalidProof("not-a-jwt");
  }
}
