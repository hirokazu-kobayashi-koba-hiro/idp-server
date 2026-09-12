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

package org.idp.server.core.openid.oauth.clientattestation;

import java.util.Objects;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * Client Attestation JWT value object.
 *
 * <p>Represents the raw Client Attestation JWT string received in the {@code
 * OAuth-Client-Attestation} HTTP request header field. The JWT is issued by a Client Attester and
 * binds the Client Instance Key ({@code cnf.jwk}) to the client ({@code sub}).
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html">OAuth
 *     2.0 Attestation-Based Client Authentication</a>
 */
public class ClientAttestationJwt {

  /** HTTP request header field name conveying the Client Attestation JWT. */
  public static final String HEADER_NAME = "OAuth-Client-Attestation";

  String value;

  public ClientAttestationJwt() {}

  public ClientAttestationJwt(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isBlank();
  }

  /**
   * Reads {@code sub} without verifying the signature, so the client can be looked up.
   *
   * <p>draft-10 Section 7.5 leaves the {@code client_id} parameter optional, because the Client
   * Attestation already names the client: "If the token request contains a client_id parameter ...
   * the Authorization Server MUST verify that the value of this parameter is the same as the
   * client_id value in the sub claim". Without this, a compliant request that omits the parameter
   * has no client to resolve and fails before reaching the authenticator.
   *
   * <p><strong>Security note:</strong> the value is unverified, exactly as {@code
   * ClientAssertion#extractIssuer} is for {@code private_key_jwt}. It only selects which client
   * configuration to load; authentication still happens in {@code ClientAttestationJwtVerifier},
   * which checks the signature and that {@code sub} equals the requested client.
   *
   * @return the subject claim value, or an empty string when it cannot be read
   */
  public String extractSubject() {
    if (!exists()) {
      return "";
    }
    try {
      JsonWebSignature jws = JsonWebSignature.parse(value);
      JsonWebTokenClaims claims = jws.claims();
      if (claims.hasSub()) {
        return claims.getSub();
      }
      return "";
    } catch (JoseInvalidException e) {
      return "";
    }
  }
}
