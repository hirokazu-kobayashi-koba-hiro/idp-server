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
   * Verifies a DPoP proof JWT and returns the verified result containing the public key.
   *
   * @param dpopProof the DPoP proof JWT string
   * @param httpMethod the HTTP method of the current request (e.g., "POST")
   * @param httpUri the HTTP URI of the current request (e.g., "https://server.example.com/token")
   * @param accessTokenHash the access token hash (ath claim), null if not required (token endpoint)
   * @return the verified DPoP proof result
   * @throws DPoPProofInvalidException if any validation check fails
   */
  public DPoPProofVerifiedResult verify(
      DPoPProof dpopProof, String httpMethod, String httpUri, String accessTokenHash) {
    if (!dpopProof.exists()) {
      throw new DPoPProofInvalidException("DPoP proof is required but not provided.");
    }

    JsonWebSignature jws;
    try {
      jws = JsonWebSignature.parse(dpopProof.value());
    } catch (JoseInvalidException e) {
      throw new DPoPProofInvalidException("DPoP proof is not a well-formed JWT: " + e.getMessage());
    }

    JsonWebSignatureHeader header = jws.header();

    // Check 4: typ must be dpop+jwt
    verifyType(header);

    // Check 5: alg must be asymmetric
    verifyAlgorithm(jws);

    // Check 7: jwk must not contain a private key
    verifyJwkPresent(header);
    JsonWebKey jwk = header.jwk();
    verifyJwkNoPrivateKey(jwk);

    // Check 6: Signature verification
    verifySignature(jws, header, jwk);

    // Check 3, 8, 9, 10, 11: Claims validation
    JsonWebTokenClaims claims = jws.claims();

    verifyRequiredClaims(claims);
    verifyHtm(claims, httpMethod);
    verifyHtu(claims, httpUri);
    verifyIat(claims);

    if (accessTokenHash != null) {
      verifyAth(claims, accessTokenHash);
    }

    JsonWebKey publicJwk = jwk.toPublicJwk();
    JwkThumbprint thumbprint = new JwkThumbprintCalculator(publicJwk).calculate();

    log.debug("DPoP proof verification succeeded, JWK Thumbprint: {}", thumbprint.value());

    return new DPoPProofVerifiedResult(publicJwk, thumbprint);
  }

  private void verifyType(JsonWebSignatureHeader header) {
    if (!header.hasType() || !DPOP_JWT_TYPE.equals(header.type())) {
      throw new DPoPProofInvalidException(
          "DPoP proof typ header must be 'dpop+jwt', but was: "
              + (header.hasType() ? header.type() : "null"));
    }
  }

  private void verifyAlgorithm(JsonWebSignature jws) {
    if (jws.isSymmetricType()) {
      throw new DPoPProofInvalidException(
          "DPoP proof alg must not be a symmetric algorithm, but was: " + jws.algorithm());
    }
    if ("none".equals(jws.algorithm())) {
      throw new DPoPProofInvalidException("DPoP proof alg must not be 'none'.");
    }
  }

  private void verifyJwkPresent(JsonWebSignatureHeader header) {
    if (!header.hasJwk()) {
      throw new DPoPProofInvalidException("DPoP proof must contain jwk JOSE Header Parameter.");
    }
  }

  private void verifyJwkNoPrivateKey(JsonWebKey jwk) {
    if (jwk.isPrivate()) {
      throw new DPoPProofInvalidException(
          "DPoP proof jwk JOSE Header Parameter must not contain a private key.");
    }
  }

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

  private void verifyHtm(JsonWebTokenClaims claims, String expectedHttpMethod) {
    String htm = claims.getValue("htm");
    if (!expectedHttpMethod.equalsIgnoreCase(htm)) {
      throw new DPoPProofInvalidException(
          String.format(
              "DPoP proof htm claim '%s' does not match the HTTP method '%s'.",
              htm, expectedHttpMethod));
    }
  }

  private void verifyHtu(JsonWebTokenClaims claims, String expectedHttpUri) {
    String htu = claims.getValue("htu");
    if (htu == null || htu.isEmpty()) {
      throw new DPoPProofInvalidException("DPoP proof htu claim is required.");
    }
    try {
      URI htuUri = URI.create(htu);
      URI expectedUri = URI.create(expectedHttpUri);
      // RFC 9449 Section 4.3: Compare scheme, authority, and path (ignoring query and fragment)
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
