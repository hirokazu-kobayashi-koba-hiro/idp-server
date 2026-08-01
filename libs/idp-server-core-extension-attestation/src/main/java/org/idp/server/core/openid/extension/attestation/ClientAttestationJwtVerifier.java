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

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.dpop.JwkThumbprint;
import org.idp.server.core.openid.oauth.dpop.JwkThumbprintCalculator;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.jose.JwtClockSkewException;
import org.idp.server.platform.jose.JwtClockSkewValidator;
import org.idp.server.platform.json.JsonConverter;

/**
 * Client Attestation JWT Verifier (draft-ietf-oauth-attestation-based-client-auth-10 Section 7.1).
 *
 * <p>Verifies the JWT conveyed by the {@code OAuth-Client-Attestation} header:
 *
 * <ol>
 *   <li>The value is a single well-formed JWT
 *   <li>The {@code typ} JOSE header has the value {@code oauth-client-attestation+jwt}
 *   <li>The signature verifies with a trusted key supplied by the {@link
 *       ClientAttestationKeyResolver}; {@code alg: none} and MAC-protected JWTs are rejected
 *   <li>The {@code alg} is in {@code client_attestation_signing_alg_values_supported} (if
 *       configured)
 *   <li>The {@code sub} claim equals the client_id of the authenticating client
 *   <li>The {@code exp} claim is present and the JWT is not expired; {@code iat}/{@code nbf} clock
 *       skew is bounded
 *   <li>The {@code cnf} claim carries the Client Instance Key in {@code jwk} representation without
 *       private key material
 * </ol>
 */
class ClientAttestationJwtVerifier {

  static final String ATTESTATION_JWT_TYPE = "oauth-client-attestation+jwt";

  /**
   * Upper bound of {@code exp - iat} accepted in the self-signed model, where the client decides
   * the lifetime of its own Client Attestation JWT.
   */
  static final long SELF_SIGNED_MAX_LIFETIME_SECONDS = 24 * 60 * 60L;

  BackchannelRequestContext context;
  ClientAttestationKeyResolver keyResolver;
  ClientAttestationTrustSource trustSource;
  JoseHandler joseHandler = new JoseHandler();

  ClientAttestationJwtVerifier(
      BackchannelRequestContext context,
      ClientAttestationKeyResolver keyResolver,
      ClientAttestationTrustSource trustSource) {
    this.context = context;
    this.keyResolver = keyResolver;
    this.trustSource = trustSource;
  }

  /**
   * Verifies the Client Attestation JWT and returns the Client Instance Key bound via {@code
   * cnf.jwk}.
   */
  JsonWebKey verify() {
    JsonWebSignatureHeader header = parseHeader();
    throwExceptionIfInvalidType(header);
    JoseContext joseContext = verifySignature(header);
    throwExceptionIfUnsupportedAlg(joseContext);
    throwExceptionIfInvalidSub(joseContext);
    throwExceptionIfInvalidExp(joseContext);
    throwExceptionIfClockSkewTooLarge(joseContext);
    JsonWebKey clientInstanceKey = extractClientInstanceKey(joseContext);

    if (trustSource.isRegisteredInstanceKey()) {
      throwExceptionIfLifetimeTooLong(joseContext);
      throwExceptionIfCnfDoesNotMatchSigningKey(joseContext, clientInstanceKey);
    }

    return clientInstanceKey;
  }

  private JsonWebSignatureHeader parseHeader() {
    try {
      return JsonWebSignature.parse(context.clientAttestationJwt().value()).header();
    } catch (JoseInvalidException e) {
      throw exception("client attestation jwt is not a well-formed JWT: " + e.getMessage(), e);
    }
  }

  private JoseContext verifySignature(JsonWebSignatureHeader header) {
    String trustedJwks = keyResolver.resolveJwks(context, header);
    if (trustedJwks == null || trustedJwks.isEmpty()) {
      throw exception("no trusted client attestation key is available for the client");
    }
    try {
      JoseContext joseContext =
          joseHandler.handle(context.clientAttestationJwt().value(), trustedJwks, trustedJwks, "");
      if (!joseContext.hasJsonWebSignature()) {
        throw exception("client attestation jwt must be signed, alg: none is not allowed");
      }
      if (joseContext.isSymmetricKey()) {
        throw exception(
            "client attestation jwt with MAC is not supported, use an asymmetric signature");
      }
      joseContext.verifySignature();
      return joseContext;
    } catch (JoseInvalidException e) {
      throw exception("client attestation jwt validation failed: " + e.getMessage(), e);
    }
  }

  private void throwExceptionIfInvalidType(JsonWebSignatureHeader header) {
    if (!header.hasType() || !ATTESTATION_JWT_TYPE.equals(header.type())) {
      throw exception(
          String.format(
              "client attestation jwt typ header must be '%s', but was: %s",
              ATTESTATION_JWT_TYPE, header.hasType() ? header.type() : "null"));
    }
  }

  private void throwExceptionIfUnsupportedAlg(JoseContext joseContext) {
    List<String> supportedAlgorithms =
        context.serverConfiguration().clientAttestationSigningAlgValuesSupported();
    String algorithm = joseContext.jsonWebSignature().algorithm();
    if (!supportedAlgorithms.isEmpty() && !supportedAlgorithms.contains(algorithm)) {
      throw exception(
          String.format(
              "client attestation jwt alg '%s' is not in client_attestation_signing_alg_values_supported: %s",
              algorithm, supportedAlgorithms));
    }
  }

  // hasXxx() only checks key presence; a JSON null value passes it, so the getter results are
  // null-checked as well.
  private void throwExceptionIfInvalidSub(JoseContext joseContext) {
    JsonWebTokenClaims claims = joseContext.claims();
    String sub = claims.getSub();
    if (sub == null || sub.isEmpty()) {
      throw exception("client attestation jwt must contain sub claim");
    }
    if (!sub.equals(context.requestedClientId().value())) {
      throw exception("client attestation jwt sub claim must be the client_id of the client");
    }
  }

  private void throwExceptionIfInvalidExp(JoseContext joseContext) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (claims.getExp() == null) {
      throw exception("client attestation jwt must contain exp claim");
    }
    if (claims.getExp().before(new Date(SystemDateTime.currentEpochMilliSecond()))) {
      throw exception("client attestation jwt is expired");
    }
  }

  private void throwExceptionIfClockSkewTooLarge(JoseContext joseContext) {
    try {
      JwtClockSkewValidator.validateIatNbf(joseContext.claims());
    } catch (JwtClockSkewException e) {
      throw exception("client attestation jwt " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private JsonWebKey extractClientInstanceKey(JoseContext joseContext) {
    Object cnf = joseContext.claims().payload().get("cnf");
    if (!(cnf instanceof Map)) {
      throw exception("client attestation jwt must contain cnf claim");
    }
    Object jwk = ((Map<String, Object>) cnf).get("jwk");
    if (!(jwk instanceof Map)) {
      throw exception("client attestation jwt cnf claim must contain a jwk representation");
    }
    try {
      JsonWebKey clientInstanceKey = JwkParser.parse(JsonConverter.snakeCaseInstance().write(jwk));
      if (clientInstanceKey.isPrivate()) {
        throw exception("client attestation jwt cnf.jwk must not contain a private key");
      }
      return clientInstanceKey;
    } catch (JsonWebKeyInvalidException e) {
      throw exception("client attestation jwt cnf.jwk is invalid: " + e.getMessage(), e);
    }
  }

  /**
   * Self-signed model: the signing key is the Client Instance Key itself, so the {@code cnf.jwk}
   * must be the very key the signature was verified with. Without this check a stolen Client
   * Attestation JWT could be paired with a PoP signed by an attacker key.
   */
  private void throwExceptionIfCnfDoesNotMatchSigningKey(
      JoseContext joseContext, JsonWebKey clientInstanceKey) {
    JsonWebKey verificationKey = joseContext.jsonWebKey();
    JwkThumbprint cnfThumbprint =
        new JwkThumbprintCalculator(clientInstanceKey.toPublicJwk()).calculate();
    JwkThumbprint signingThumbprint =
        new JwkThumbprintCalculator(verificationKey.toPublicJwk()).calculate();
    if (!cnfThumbprint.value().equals(signingThumbprint.value())) {
      throw exception(
          "client attestation jwt cnf.jwk must be the registered client instance key that signed it");
    }
  }

  /** Self-signed model: the client controls exp, so the server bounds the lifetime. */
  private void throwExceptionIfLifetimeTooLong(JoseContext joseContext) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (claims.getIat() == null) {
      throw exception("client attestation jwt must contain iat claim");
    }
    long lifetimeSeconds = (claims.getExp().getTime() - claims.getIat().getTime()) / 1000L;
    if (lifetimeSeconds > SELF_SIGNED_MAX_LIFETIME_SECONDS) {
      throw exception(
          String.format(
              "client attestation jwt lifetime (exp - iat) must not exceed %d seconds, but was: %d",
              SELF_SIGNED_MAX_LIFETIME_SECONDS, lifetimeSeconds));
    }
  }

  private ClientUnAuthorizedException exception(String message) {
    return new ClientUnAuthorizedException(
        ClientAuthenticationType.attest_jwt_client_auth.name(),
        context.requestedClientId(),
        message);
  }

  private ClientUnAuthorizedException exception(String message, Throwable cause) {
    return new ClientUnAuthorizedException(
        ClientAuthenticationType.attest_jwt_client_auth.name(),
        context.requestedClientId(),
        message,
        cause);
  }
}
