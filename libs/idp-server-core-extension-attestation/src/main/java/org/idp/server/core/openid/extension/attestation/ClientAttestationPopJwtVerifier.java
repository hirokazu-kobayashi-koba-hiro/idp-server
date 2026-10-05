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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenges;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.InvalidClientAttestationException;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.UseAttestationChallengeException;
import org.idp.server.core.openid.oauth.replay.JwtReplayDetector;
import org.idp.server.core.openid.oauth.replay.JwtReplayKind;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.jose.JsonWebSignatureVerifier;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.jose.JsonWebTokenClaimsInvalidException;
import org.idp.server.platform.jose.JwtClockSkewException;
import org.idp.server.platform.jose.JwtClockSkewValidator;

/**
 * Client Attestation PoP JWT Verifier (draft-ietf-oauth-attestation-based-client-auth-11 Section
 * 7.2).
 *
 * <p>Verifies the JWT conveyed by the {@code OAuth-Client-Attestation-PoP} header:
 *
 * <ol>
 *   <li>The value is a single well-formed JWT
 *   <li>The {@code typ} JOSE header has the value {@code oauth-client-attestation-pop+jwt}
 *   <li>The {@code alg} is an asymmetric digital signature algorithm (not {@code none}, not a MAC)
 *       and is in {@code client_attestation_pop_signing_alg_values_supported} (if configured)
 *   <li>The signature verifies with the Client Instance Key bound in the Client Attestation JWT
 *       ({@code cnf.jwk})
 *   <li>The {@code aud} claim contains the issuer identifier URL of this authorization server
 *   <li>The {@code jti} claim is present
 *   <li>The {@code iat} claim is present and within an acceptable time window
 * </ol>
 *
 * <p>The {@code challenge} claim is verified last, after the PoP has otherwise proven possession of
 * the Client Instance Key: rejecting it mints a fresh Challenge (Section 7.4 requires the error to
 * carry one), so an unauthenticated caller must not be able to drive that write.
 *
 * <p>Replay detection of {@code jti} (Section 12.1, SHOULD): each accepted PoP is recorded per
 * client for as long as its {@code iat} window lets it through, and refused the second time (Issue
 * #1893). Without a detector, the window alone limits reuse.
 */
class ClientAttestationPopJwtVerifier {

  static final String POP_JWT_TYPE = "oauth-client-attestation-pop+jwt";

  BackchannelRequestContext context;
  JsonWebKey clientInstanceKey;
  ClientAttestationChallenges challenges;
  JwtReplayDetector replayDetector;

  ClientAttestationPopJwtVerifier(
      BackchannelRequestContext context,
      JsonWebKey clientInstanceKey,
      ClientAttestationChallenges challenges,
      JwtReplayDetector replayDetector) {
    this.context = context;
    this.clientInstanceKey = clientInstanceKey;
    this.challenges = challenges;
    this.replayDetector = replayDetector;
  }

  /** Verifies the Client Attestation PoP JWT and returns the verified JWS. */
  JsonWebSignature verify() {
    JsonWebSignature jws = parse();
    throwExceptionIfInvalidType(jws.header());
    throwExceptionIfInvalidAlg(jws);
    verifySignature(jws);
    JsonWebTokenClaims claims = claimsOf(jws);
    throwExceptionIfInvalidAud(claims);
    throwExceptionIfInvalidJti(claims);
    throwExceptionIfInvalidIat(claims);
    throwExceptionIfInvalidChallenge(claims);
    // Last, so that only a PoP that passed every other check uses up its jti.
    throwExceptionIfReplayed(claims);
    return jws;
  }

  /**
   * The PoP is signed with the instance key the client itself presents, so a valid signature over a
   * payload that is not a claims set is within anyone's reach; it is a malformed PoP, not an error.
   */
  private JsonWebTokenClaims claimsOf(JsonWebSignature jws) {
    try {
      return jws.claims();
    } catch (JsonWebTokenClaimsInvalidException e) {
      throw exception("client attestation pop jwt payload is not a claims set", e);
    }
  }

  private JsonWebSignature parse() {
    try {
      return JsonWebSignature.parse(context.clientAttestationPopJwt().value());
    } catch (JoseInvalidException e) {
      throw exception("client attestation pop jwt is not a well-formed JWT: " + e.getMessage(), e);
    }
  }

  private void throwExceptionIfInvalidType(JsonWebSignatureHeader header) {
    if (!header.hasType() || !POP_JWT_TYPE.equals(header.type())) {
      throw exception(
          String.format(
              "client attestation pop jwt typ header must be '%s', but was: %s",
              POP_JWT_TYPE, header.hasType() ? header.type() : "null"));
    }
  }

  private void throwExceptionIfInvalidAlg(JsonWebSignature jws) {
    if ("none".equals(jws.algorithm())) {
      throw exception("client attestation pop jwt alg must not be 'none'");
    }
    if (jws.isSymmetricType()) {
      throw exception(
          "client attestation pop jwt must be signed with an asymmetric algorithm, but was: "
              + jws.algorithm());
    }
    List<String> supportedAlgorithms =
        context.serverConfiguration().clientAttestationPopSigningAlgValuesSupported();
    if (!supportedAlgorithms.isEmpty() && !supportedAlgorithms.contains(jws.algorithm())) {
      throw exception(
          String.format(
              "client attestation pop jwt alg '%s' is not in client_attestation_pop_signing_alg_values_supported: %s",
              jws.algorithm(), supportedAlgorithms));
    }
  }

  private void verifySignature(JsonWebSignature jws) {
    try {
      JsonWebSignatureVerifier verifier =
          new JsonWebSignatureVerifier(jws.header(), clientInstanceKey.toPublicKey());
      verifier.verify(jws);
    } catch (JoseInvalidException | JsonWebKeyInvalidException e) {
      throw exception(
          "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk): "
              + e.getMessage(),
          e);
    }
  }

  // hasXxx() only checks key presence; a JSON null value passes it, so the getter results are
  // null-checked as well.
  private void throwExceptionIfInvalidAud(JsonWebTokenClaims claims) {
    List<String> aud = claims.getAud();
    if (aud == null || aud.isEmpty()) {
      throw exception("client attestation pop jwt must contain aud claim");
    }
    String issuer = context.serverConfiguration().tokenIssuer().value();
    if (!aud.contains(issuer)) {
      throw exception(
          "client attestation pop jwt aud claim must be the issuer identifier URL of the authorization server");
    }
  }

  private void throwExceptionIfInvalidJti(JsonWebTokenClaims claims) {
    String jti = claims.getJti();
    if (jti == null || jti.isEmpty()) {
      throw exception("client attestation pop jwt must contain jti claim");
    }
  }

  private void throwExceptionIfInvalidIat(JsonWebTokenClaims claims) {
    if (claims.getIat() == null) {
      throw exception("client attestation pop jwt must contain iat claim");
    }
    Instant issuedAt = claims.getIat().toInstant();
    Duration window = acceptableTimeWindow();
    Duration difference = Duration.between(issuedAt, Instant.now()).abs();
    if (difference.compareTo(window) > 0) {
      throw exception(
          String.format(
              "client attestation pop jwt iat claim is outside the acceptable time window. Issued at: %s, Allowed window: %s",
              issuedAt, window));
    }
    try {
      JwtClockSkewValidator.validateIatNbf(claims);
    } catch (JwtClockSkewException e) {
      throw exception("client attestation pop jwt " + e.getMessage());
    }
  }

  /** {@code client_attestation_pop_acceptable_window_seconds} (default 60 seconds, Issue #1893). */
  private Duration acceptableTimeWindow() {
    return context.serverConfiguration().clientAttestationPopAcceptableWindow();
  }

  /**
   * Section 12.1: the PoP has not been used before. Keyed by the client and {@code jti}, so that
   * one client cannot use up the {@code jti} of another.
   */
  private void throwExceptionIfReplayed(JsonWebTokenClaims claims) {
    if (replayDetector == null) {
      return;
    }
    boolean firstUse =
        replayDetector.firstUse(
            context.tenant(),
            JwtReplayKind.CLIENT_ATTESTATION_POP,
            context.requestedClientId().value(),
            claims.getJti(),
            claims.getIat().toInstant(),
            acceptableTimeWindow());
    if (!firstUse) {
      throw exception("client attestation pop jwt has already been used");
    }
  }

  /**
   * Section 7.2 item 5: a Challenge the server provided has to come back in the {@code challenge}
   * claim. Section 6.1 answers a missing or rejected one with {@code use_attestation_challenge}. A
   * presented challenge is always validated; whether one is mandatory is a tenant policy, so that a
   * deployment can advertise the challenge endpoint and let its clients adopt it before requests
   * without a challenge start failing.
   */
  private void throwExceptionIfInvalidChallenge(JsonWebTokenClaims claims) {
    String presented = claims.getValue("challenge");

    if (presented.isEmpty()) {
      if (!context.isClientAttestationChallengeRequired()) {
        return;
      }
      throw useAttestationChallengeException(
          "client attestation pop jwt must contain challenge claim");
    }

    ClientAttestationChallenge stored = challenges.find(context.tenant(), presented);
    if (!stored.isValid()) {
      throw useAttestationChallengeException(
          "client attestation pop jwt challenge claim is unknown or expired");
    }
  }

  /**
   * Section 7.4 requires {@code use_attestation_challenge} to be accompanied by a fresh Challenge,
   * so one is issued here and travels back on the {@code OAuth-Client-Attestation-Challenge}
   * response header.
   */
  private UseAttestationChallengeException useAttestationChallengeException(String message) {
    ClientAttestationChallenge fresh =
        challenges.issue(
            context.tenant(), context.serverConfiguration().clientAttestationChallengeDuration());

    return new UseAttestationChallengeException(
        ClientAuthenticationType.attest_jwt_client_auth.name(),
        context.requestedClientId(),
        message,
        fresh.value());
  }

  private InvalidClientAttestationException exception(String message) {
    return new InvalidClientAttestationException(
        ClientAuthenticationType.attest_jwt_client_auth.name(),
        context.requestedClientId(),
        message);
  }

  private InvalidClientAttestationException exception(String message, Throwable cause) {
    return new InvalidClientAttestationException(
        ClientAuthenticationType.attest_jwt_client_auth.name(),
        context.requestedClientId(),
        message,
        cause);
  }
}
