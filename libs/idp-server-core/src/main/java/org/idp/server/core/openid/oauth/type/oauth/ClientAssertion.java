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

package org.idp.server.core.openid.oauth.type.oauth;

import java.util.Objects;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;

/**
 * ClientAssertion
 *
 * <p>Represents a client_assertion parameter used for JWT-based client authentication as defined in
 * RFC 7521 and RFC 7523.
 *
 * <p>Per RFC 7521 Section 4.2, when using assertion-based client authentication, the client_id
 * parameter is OPTIONAL because the client can be identified by the subject (sub) or issuer (iss)
 * claim within the assertion JWT.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7521#section-4.2">RFC 7521 Section 4.2</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523#section-3">RFC 7523 Section 3</a>
 */
public class ClientAssertion {
  String value;

  public ClientAssertion() {}

  public ClientAssertion(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  /**
   * Extracts the issuer (iss) claim from the JWT without signature verification.
   *
   * <p>Per RFC 7523 Section 3, for client authentication, the "iss" claim MUST contain the
   * client_id of the OAuth client. This method decodes the JWT payload to extract the issuer claim
   * for client identification purposes.
   *
   * <p><strong>Security Note:</strong> This method only decodes the JWT without verifying the
   * signature. The extracted issuer is used to look up the client configuration and retrieve the
   * client's public key for subsequent signature verification. The actual authentication is
   * completed only after successful signature verification.
   *
   * @return the issuer claim value, or empty string if extraction fails
   */
  public String extractIssuer() {
    if (!exists()) {
      return "";
    }
    try {
      JsonWebSignature jws = JsonWebSignature.parse(value);
      JsonWebTokenClaims claims = jws.claims();
      if (claims.hasIss()) {
        return claims.getIss();
      }
      return "";
    } catch (JoseInvalidException e) {
      return "";
    }
  }

  /**
   * Checks if the issuer can be extracted from the JWT.
   *
   * @return true if the JWT contains a valid issuer claim
   */
  public boolean hasExtractableIssuer() {
    String issuer = extractIssuer();
    return Objects.nonNull(issuer) && !issuer.isEmpty();
  }
}
