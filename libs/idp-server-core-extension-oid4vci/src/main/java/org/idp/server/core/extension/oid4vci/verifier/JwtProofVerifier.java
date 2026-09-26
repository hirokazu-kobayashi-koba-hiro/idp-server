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

import java.time.Instant;
import java.util.List;
import org.idp.server.core.extension.oid4vci.exception.CredentialRequestInvalidException;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialProofTypeConfiguration;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.jose.JsonWebSignatureVerifier;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * Verifies one {@code jwt} key proof (OpenID4VCI 1.0 Appendix F.1, checks of Appendix F.4).
 *
 * <p>Every failure is {@code invalid_proof}. Whether the {@code nonce} is one this issuer handed
 * out is decided afterwards, against the nonce store, and fails as {@code invalid_nonce}: the proof
 * itself is well-formed in that case, the Wallet only needs a fresh nonce.
 *
 * <p>The key the credential is bound to comes in the {@code jwk} header. {@code kid} (a DID URL)
 * and {@code x5c} are not supported: the only binding method this issuer publishes is {@code jwk}.
 */
public class JwtProofVerifier {

  static final String PROOF_TYPE = "openid4vci-proof+jwt";

  /** How old a proof may be (Section 13.8); the nonce is the real freshness guarantee. */
  static final long MAX_AGE_SECONDS = 300;

  /** How far ahead of this server's clock a proof may be dated. */
  static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;

  String proof;
  CredentialProofTypeConfiguration proofTypeConfiguration;
  String credentialIssuer;
  String clientId;

  public JwtProofVerifier(
      String proof,
      CredentialProofTypeConfiguration proofTypeConfiguration,
      String credentialIssuer,
      String clientId) {
    this.proof = proof;
    this.proofTypeConfiguration = proofTypeConfiguration;
    this.credentialIssuer = credentialIssuer;
    this.clientId = clientId;
  }

  public VerifiedJwtProof verify() {
    JsonWebSignature jws = parse();
    JsonWebSignatureHeader header = jws.header();

    throwExceptionIfInvalidType(header);
    throwExceptionIfUnsupportedAlgorithm(jws);
    JsonWebKey holderKey = holderKeyOf(header);
    throwExceptionIfInvalidSignature(jws, holderKey);

    JsonWebTokenClaims claims = jws.claims();
    throwExceptionIfInvalidIss(claims);
    throwExceptionIfInvalidAud(claims);
    throwExceptionIfInvalidIat(claims);
    String nonce = nonceOf(claims);

    return new VerifiedJwtProof(holderKey.toPublicJwk(), nonce);
  }

  JsonWebSignature parse() {
    try {
      return JsonWebSignature.parse(proof);
    } catch (Exception e) {
      throw invalidProof("key proof is not a signed JWT");
    }
  }

  void throwExceptionIfInvalidType(JsonWebSignatureHeader header) {
    if (!header.hasType() || !PROOF_TYPE.equals(header.type())) {
      throw invalidProof("typ of the key proof must be " + PROOF_TYPE);
    }
  }

  /**
   * Appendix F.1: alg "MUST NOT be none or an identifier for a symmetric algorithm (MAC)" and "MUST
   * match one of the values listed in the proof_signing_alg_values_supported".
   */
  void throwExceptionIfUnsupportedAlgorithm(JsonWebSignature jws) {
    if (jws.isSymmetricType()) {
      throw invalidProof("key proof must not use a symmetric algorithm");
    }
    if (!proofTypeConfiguration.supportsSigningAlg(jws.algorithm())) {
      throw invalidProof("unsupported key proof algorithm: " + jws.algorithm());
    }
  }

  /** Appendix F.1: kid, jwk and x5c are mutually exclusive; F.4: no private key in the header. */
  JsonWebKey holderKeyOf(JsonWebSignatureHeader header) {
    int keyParameters =
        (header.hasKid() ? 1 : 0) + (header.hasJwk() ? 1 : 0) + (header.hasX5c() ? 1 : 0);
    if (keyParameters > 1) {
      throw invalidProof("only one of kid, jwk and x5c may be present in the key proof");
    }
    if (!header.hasJwk()) {
      throw invalidProof("the key proof must carry the key to bind in the jwk header");
    }
    JsonWebKey jwk = header.jwk();
    if (jwk.isPrivate()) {
      throw invalidProof("the jwk header of the key proof must not contain a private key");
    }
    return jwk;
  }

  void throwExceptionIfInvalidSignature(JsonWebSignature jws, JsonWebKey holderKey) {
    try {
      new JsonWebSignatureVerifier(jws.header(), holderKey.toPublicKey()).verify(jws);
    } catch (Exception e) {
      throw invalidProof("signature of the key proof is invalid");
    }
  }

  /** iss: OPTIONAL. "The value of this claim MUST be the client_id of the Client". */
  void throwExceptionIfInvalidIss(JsonWebTokenClaims claims) {
    if (claims.hasIss() && !claims.getIss().equals(clientId)) {
      throw invalidProof("iss of the key proof must be the client_id");
    }
  }

  /** aud: REQUIRED. "The value of this claim MUST be the Credential Issuer Identifier." */
  void throwExceptionIfInvalidAud(JsonWebTokenClaims claims) {
    List<String> aud = claims.hasAud() ? claims.getAud() : List.of();
    if (aud.size() != 1 || !aud.get(0).equals(credentialIssuer)) {
      throw invalidProof("aud of the key proof must be the Credential Issuer Identifier");
    }
  }

  /** iat: REQUIRED, and within an acceptable window (Appendix F.4, Section 13.8). */
  void throwExceptionIfInvalidIat(JsonWebTokenClaims claims) {
    if (!claims.hasIat()) {
      throw invalidProof("iat is required in the key proof");
    }
    Instant iat = claims.getIat().toInstant();
    Instant now = Instant.ofEpochMilli(SystemDateTime.currentEpochMilliSecond());
    if (iat.isAfter(now.plusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
      throw invalidProof("iat of the key proof is in the future");
    }
    if (iat.isBefore(now.minusSeconds(MAX_AGE_SECONDS))) {
      throw invalidProof("the key proof is too old");
    }
  }

  /**
   * nonce: "It MUST be present when the issuer has a Nonce Endpoint"; a proof without one is {@code
   * invalid_proof} (Section 8.3.1.2 case (3)).
   */
  String nonceOf(JsonWebTokenClaims claims) {
    Object nonce = claims.payload().get("nonce");
    if (!(nonce instanceof String value) || value.isEmpty()) {
      throw invalidProof("nonce is required in the key proof");
    }
    return value;
  }

  CredentialRequestInvalidException invalidProof(String description) {
    return new CredentialRequestInvalidException("invalid_proof", description);
  }
}
