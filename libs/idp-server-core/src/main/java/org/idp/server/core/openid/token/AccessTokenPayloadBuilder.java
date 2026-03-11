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

package org.idp.server.core.openid.token;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.dpop.JwkThumbprint;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.extension.CreatedAt;
import org.idp.server.core.openid.oauth.type.extension.ExpiresAt;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oauth.Subject;
import org.idp.server.core.openid.oauth.type.oauth.TokenIssuer;
import org.idp.server.platform.date.SystemDateTime;

public class AccessTokenPayloadBuilder {
  Map<String, Object> values = new HashMap<>();

  public AccessTokenPayloadBuilder() {}

  public AccessTokenPayloadBuilder add(TokenIssuer tokenIssuer) {
    values.put("iss", tokenIssuer.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Subject subject) {
    if (subject.exists()) {
      values.put("sub", subject.value());
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(RequestedClientId requestedClientId) {
    values.put("client_id", requestedClientId.value());
    return this;
  }

  public AccessTokenPayloadBuilder add(Scopes scopes) {
    values.put("scope", scopes.toStringValues());
    return this;
  }

  public AccessTokenPayloadBuilder addCustomClaims(Map<String, Object> customClaims) {
    if (!customClaims.isEmpty()) {
      values.putAll(customClaims);
    }
    return this;
  }

  public AccessTokenPayloadBuilder addJti(String jti) {
    values.put("jti", jti);
    return this;
  }

  public AccessTokenPayloadBuilder add(CreatedAt createdAt) {
    values.put("iat", createdAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(ExpiresAt expiresAt) {
    values.put("exp", expiresAt.toEpochSecondWithUtc());
    return this;
  }

  public AccessTokenPayloadBuilder add(AuthorizationDetails authorizationDetails) {
    if (authorizationDetails.exists()) {
      values.put("authorization_details", authorizationDetails.toMapValues());
    }
    return this;
  }

  /**
   * Add sender-constrained token confirmation claims (cnf) to JWT access token.
   *
   * <p>Supports both mTLS (RFC 8705, x5t#S256) and DPoP (RFC 9449, jkt) bindings, which can coexist
   * in a single cnf claim object.
   *
   * @param certThumbprint the mTLS certificate thumbprint (RFC 8705)
   * @param jwkThumbprint the DPoP JWK Thumbprint (RFC 9449)
   * @return this builder
   */
  public AccessTokenPayloadBuilder addConfirmation(
      ClientCertificationThumbprint certThumbprint, JwkThumbprint jwkThumbprint) {
    Map<String, Object> cnf = new LinkedHashMap<>();
    if (jwkThumbprint != null && jwkThumbprint.exists()) {
      cnf.put("jkt", jwkThumbprint.value());
    }
    if (certThumbprint != null && certThumbprint.exists()) {
      cnf.put("x5t#S256", certThumbprint.value());
    }
    if (!cnf.isEmpty()) {
      values.put("cnf", cnf);
    }
    return this;
  }

  public AccessTokenPayloadBuilder add(ClientCertificationThumbprint thumbprint) {
    if (thumbprint.exists()) {
      values.put("cnf", Map.of("x5t#S256", thumbprint.value()));
    }
    return this;
  }

  /**
   * Add DPoP JWK Thumbprint confirmation claim to JWT access token.
   *
   * <p>RFC 9449 Section 6: The confirmation claim "cnf" with "jkt" member is used to bind the
   * access token to the DPoP public key.
   *
   * @param jwkThumbprint the JWK Thumbprint (RFC 7638) of the DPoP public key
   * @return this builder
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9449.html#section-6">RFC 9449 Section 6</a>
   */
  public AccessTokenPayloadBuilder add(JwkThumbprint jwkThumbprint) {
    if (jwkThumbprint.exists()) {
      values.put("cnf", Map.of("jkt", jwkThumbprint.value()));
    }
    return this;
  }

  /**
   * Add authentication information claims to JWT access token
   *
   * <p>RFC 9068 Section 2.2.1 allows JWT access tokens to contain authentication information claims
   * (auth_time, acr, amr) to enable resource servers to verify authentication context.
   *
   * @param authentication the authentication information
   * @return this builder
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9068#section-2.2.1">RFC 9068 Section 2.2.1</a>
   */
  public AccessTokenPayloadBuilder add(Authentication authentication) {
    if (authentication.hasAuthenticationTime()) {
      values.put("auth_time", SystemDateTime.toEpochSecond(authentication.time()));
    }
    if (authentication.hasMethod()) {
      values.put("amr", authentication.methods());
    }
    if (authentication.hasAcrValues()) {
      values.put("acr", authentication.acr());
    }
    return this;
  }

  public Map<String, Object> build() {
    return values;
  }
}
