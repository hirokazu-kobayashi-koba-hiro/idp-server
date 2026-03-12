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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.jose.JsonWebSignatureVerifier;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * DPoP Proof JWT Verifier (RFC 9449 Section 4.3).
 *
 * <p>Validates a DPoP proof JWT according to the 12 checks defined in Section 4.3 of RFC 9449:
 *
 * <ol>
 *   <li>There is not more than one DPoP HTTP request header field
 *   <li>The DPoP HTTP request header field value is a single well-formed JWT
 *   <li>All required claims are contained in the JWT
 *   <li>The typ JOSE Header Parameter has the value dpop+jwt
 *   <li>The alg JOSE Header Parameter indicates a registered asymmetric digital signature algorithm
 *   <li>The JWT signature verifies with the public key contained in the jwk JOSE Header Parameter
 *   <li>The jwk JOSE Header Parameter does not contain a private key
 *   <li>The htm claim matches the HTTP method of the current request
 *   <li>The htu claim matches the HTTP URI of the current request
 *   <li>If the server provided a nonce, the nonce claim matches the server-provided nonce
 *   <li>The creation time (iat) is within an acceptable window
 *   <li>If replay protection is desired, the jti value has not been used before
 * </ol>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
 */
public class DPoPProofVerifier {

  static final LoggerWrapper log = LoggerWrapper.getLogger(DPoPProofVerifier.class);
  static final Duration DEFAULT_ACCEPTABLE_TIME_WINDOW = Duration.ofMinutes(5);
  static final String DPOP_JWT_TYPE = "dpop+jwt";

  /**
   * Verifies a DPoP proof if present, skipping verification when no proof is provided.
   *
   * <p>This method is intended for the token endpoint (RFC 9449 Section 4), where DPoP is optional.
   * If the client does not send a DPoP header, the server issues a regular Bearer token.
   *
   * @param dpopProof the DPoP proof JWT, or null if the DPoP header was absent
   * @param httpMethod the HTTP method of the current request (e.g., "POST")
   * @param httpUri the HTTP URI of the current request
   * @return the verified result, or empty result if no DPoP proof was provided
   * @throws DPoPProofInvalidException if proof is present but invalid
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4">RFC 9449 Section 4</a>
   */
  public DPoPProofVerifiedResult verifyIfNeeded(
      DPoPProof dpopProof, String httpMethod, String httpUri) {
    if (dpopProof == null) {
      return new DPoPProofVerifiedResult();
    }
    if (dpopProof.isPresentButEmpty()) {
      throw new DPoPProofInvalidException("DPoP header is present but empty");
    }
    if (!dpopProof.exists()) {
      return new DPoPProofVerifiedResult();
    }
    return verify(dpopProof, httpMethod, httpUri, null);
  }

  /**
   * Verifies a DPoP proof JWT and returns the verified result containing the public key.
   *
   * <p>Performs all applicable checks from RFC 9449 Section 4.3. The {@code accessTokenHash}
   * parameter controls whether the {@code ath} claim is verified:
   *
   * <ul>
   *   <li>Token endpoint: {@code accessTokenHash} is null (ath is not required)
   *   <li>Resource endpoint (e.g., UserInfo): {@code accessTokenHash} is the base64url-encoded
   *       SHA-256 hash of the ASCII access token value (RFC 9449 Section 4.2)
   * </ul>
   *
   * @param dpopProof the DPoP proof JWT string
   * @param httpMethod the HTTP method of the current request (e.g., "POST", "GET")
   * @param httpUri the HTTP URI of the current request
   * @param accessTokenHash the access token hash (ath claim), null if not required
   * @return the verified DPoP proof result containing the public key and JWK thumbprint
   * @throws DPoPProofInvalidException if any validation check fails
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
   */
  public DPoPProofVerifiedResult verify(
      DPoPProof dpopProof, String httpMethod, String httpUri, String accessTokenHash) {
    if (dpopProof.isPresentButEmpty()) {
      throw new DPoPProofInvalidException("DPoP header is present but empty");
    }
    if (!dpopProof.exists()) {
      throw new DPoPProofInvalidException("DPoP proof is required but not provided.");
    }

    // Check 2: The DPoP HTTP request header field value is a single well-formed JWT
    JsonWebSignature jws;
    try {
      jws = JsonWebSignature.parse(dpopProof.value());
    } catch (JoseInvalidException e) {
      throw new DPoPProofInvalidException("DPoP proof is not a well-formed JWT: " + e.getMessage());
    }

    JsonWebSignatureHeader header = jws.header();

    // Check 4: The typ JOSE Header Parameter has the value dpop+jwt
    verifyType(header);

    // Check 5: The alg JOSE Header Parameter indicates a registered asymmetric digital signature
    // algorithm
    verifyAlgorithm(jws);

    // Check 7: The jwk JOSE Header Parameter does not contain a private key
    verifyJwkPresent(header);
    JsonWebKey jwk = header.jwk();
    verifyJwkNoPrivateKey(jwk);

    // Check 6: The JWT signature verifies with the public key contained in the jwk JOSE Header
    // Parameter
    verifySignature(jws, header, jwk);

    JsonWebTokenClaims claims = jws.claims();

    // Check 3: All required claims (jti, htm, htu, iat) are contained in the JWT
    verifyRequiredClaims(claims);

    // Check 8: The htm claim matches the HTTP method of the current request
    verifyHtm(claims, httpMethod);

    // Check 9: The htu claim matches the HTTP URI of the current request (scheme, authority, path)
    verifyHtu(claims, httpUri);

    // Check 11: The creation time (iat) is within an acceptable window
    verifyIat(claims);

    // RFC 9449 Section 4.2: When the DPoP proof is used in conjunction with an access token,
    // the ath claim MUST be included and match the hash of the access token
    if (accessTokenHash != null) {
      verifyAth(claims, accessTokenHash);
    }

    // Check 12: jti replay protection (RFC 9449 Section 4.3)
    // Currently not implemented. The RFC states this as conditional ("if replay protection is
    // desired"). A future implementation would require a time-limited jti cache store
    // (e.g., Redis with TTL matching the iat acceptance window) to detect reuse.

    // Calculate JWK Thumbprint (RFC 7638) for token binding (RFC 9449 Section 6)
    JsonWebKey publicJwk = jwk.toPublicJwk();
    JwkThumbprint thumbprint = new JwkThumbprintCalculator(publicJwk).calculate();

    log.debug("DPoP proof verification succeeded, JWK Thumbprint: {}", thumbprint.value());

    return new DPoPProofVerifiedResult(publicJwk, thumbprint);
  }

  /**
   * Check 4: The typ JOSE Header Parameter has the value dpop+jwt.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
   */
  private void verifyType(JsonWebSignatureHeader header) {
    if (!header.hasType() || !DPOP_JWT_TYPE.equals(header.type())) {
      throw new DPoPProofInvalidException(
          "DPoP proof typ header must be 'dpop+jwt', but was: "
              + (header.hasType() ? header.type() : "null"));
    }
  }

  /**
   * Check 5: The alg JOSE Header Parameter indicates a registered asymmetric digital signature
   * algorithm, not "none".
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
   */
  private void verifyAlgorithm(JsonWebSignature jws) {
    if (jws.isSymmetricType()) {
      throw new DPoPProofInvalidException(
          "DPoP proof alg must not be a symmetric algorithm, but was: " + jws.algorithm());
    }
    if ("none".equals(jws.algorithm())) {
      throw new DPoPProofInvalidException("DPoP proof alg must not be 'none'.");
    }
  }

  /**
   * Check 7 (part 1): The jwk JOSE Header Parameter is present.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
   */
  private void verifyJwkPresent(JsonWebSignatureHeader header) {
    if (!header.hasJwk()) {
      throw new DPoPProofInvalidException("DPoP proof must contain jwk JOSE Header Parameter.");
    }
  }

  /**
   * Check 7 (part 2): The jwk JOSE Header Parameter does not contain a private key.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
   */
  private void verifyJwkNoPrivateKey(JsonWebKey jwk) {
    if (jwk.isPrivate()) {
      throw new DPoPProofInvalidException(
          "DPoP proof jwk JOSE Header Parameter must not contain a private key.");
    }
  }

  /**
   * Check 6: The JWT signature verifies with the public key contained in the jwk JOSE Header
   * Parameter.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
   */
  private void verifySignature(
      JsonWebSignature jws, JsonWebSignatureHeader header, JsonWebKey jwk) {
    try {
      JsonWebSignatureVerifier verifier = new JsonWebSignatureVerifier(header, jwk.toPublicKey());
      verifier.verify(jws);
    } catch (JoseInvalidException | JsonWebKeyInvalidException e) {
      throw new DPoPProofInvalidException(
          "DPoP proof signature verification failed: " + e.getMessage());
    }
  }

  /**
   * Check 3: All required claims (jti, htm, htu, iat) are contained in the JWT.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
   */
  private void verifyRequiredClaims(JsonWebTokenClaims claims) {
    if (!claims.hasJti() || claims.getJti().isEmpty()) {
      throw new DPoPProofInvalidException("DPoP proof must contain jti claim.");
    }
    if (!claims.contains("htm")) {
      throw new DPoPProofInvalidException("DPoP proof must contain htm claim.");
    }
    if (!claims.contains("htu")) {
      throw new DPoPProofInvalidException("DPoP proof must contain htu claim.");
    }
    if (!claims.hasIat()) {
      throw new DPoPProofInvalidException("DPoP proof must contain iat claim.");
    }
  }

  /**
   * Check 8: The htm claim matches the HTTP method of the current request.
   *
   * <p>The value of the htm claim is compared case-insensitively.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
   */
  private void verifyHtm(JsonWebTokenClaims claims, String expectedHttpMethod) {
    String htm = claims.getValue("htm");
    if (!expectedHttpMethod.equalsIgnoreCase(htm)) {
      throw new DPoPProofInvalidException(
          String.format(
              "DPoP proof htm claim '%s' does not match the HTTP method '%s'.",
              htm, expectedHttpMethod));
    }
  }

  /**
   * Check 9: The htu claim matches the HTTP URI of the current request.
   *
   * <p>Per RFC 9449 Section 4.3, the comparison is performed on scheme, authority, and path only,
   * ignoring query and fragment components.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.3">RFC 9449 Section 4.3</a>
   */
  private void verifyHtu(JsonWebTokenClaims claims, String expectedHttpUri) {
    String htu = claims.getValue("htu");
    if (htu == null || htu.isEmpty()) {
      throw new DPoPProofInvalidException("DPoP proof htu claim is required.");
    }
    try {
      URI htuUri = URI.create(htu);
      URI expectedUri = URI.create(expectedHttpUri);
      String htuNormalized = htuUri.getScheme() + "://" + htuUri.getAuthority() + htuUri.getPath();
      String expectedNormalized =
          expectedUri.getScheme() + "://" + expectedUri.getAuthority() + expectedUri.getPath();
      if (!htuNormalized.equalsIgnoreCase(expectedNormalized)) {
        throw new DPoPProofInvalidException(
            String.format(
                "DPoP proof htu claim '%s' does not match the HTTP URI '%s'.",
                htu, expectedHttpUri));
      }
    } catch (IllegalArgumentException e) {
      throw new DPoPProofInvalidException("DPoP proof htu claim is not a valid URI: " + htu);
    }
  }

  /**
   * Check 11: The creation time (iat) is within an acceptable window.
   *
   * <p>The acceptable window is {@value #DEFAULT_ACCEPTABLE_TIME_WINDOW} minutes. If the absolute
   * difference between the current time and iat exceeds this window, the proof is rejected.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-11.1">RFC 9449 Section
   *     11.1</a>
   */
  private void verifyIat(JsonWebTokenClaims claims) {
    Date iat = claims.getIat();
    Instant now = Instant.now();
    Instant issuedAt = iat.toInstant();
    Duration difference = Duration.between(issuedAt, now).abs();
    if (difference.compareTo(DEFAULT_ACCEPTABLE_TIME_WINDOW) > 0) {
      throw new DPoPProofInvalidException(
          String.format(
              "DPoP proof iat claim is outside the acceptable time window. "
                  + "Issued at: %s, Current time: %s, Allowed window: %s.",
              issuedAt, now, DEFAULT_ACCEPTABLE_TIME_WINDOW));
    }
  }

  /**
   * Verifies the ath (access token hash) claim.
   *
   * <p>Per RFC 9449 Section 4.2, when a DPoP proof is used in conjunction with an access token, the
   * proof MUST include an ath claim whose value is the base64url encoding of the SHA-256 hash of
   * the ASCII encoding of the associated access token's value.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-4.2">RFC 9449 Section 4.2</a>
   */
  private void verifyAth(JsonWebTokenClaims claims, String expectedAccessTokenHash) {
    String ath = claims.getValue("ath");
    if (ath == null || ath.isEmpty()) {
      throw new DPoPProofInvalidException(
          "DPoP proof must contain ath claim when used with an access token.");
    }
    if (!expectedAccessTokenHash.equals(ath)) {
      throw new DPoPProofInvalidException(
          "DPoP proof ath claim does not match the access token hash.");
    }
  }
}
