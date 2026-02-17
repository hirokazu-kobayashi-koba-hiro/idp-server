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

package org.idp.server.core.openid.oauth.verifier.extension;

import java.util.Date;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.RequestObjectInvalidException;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

public interface RequestObjectVerifyable {

  default void verify(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    throwExceptionIfSymmetricKey(
        joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfContainsRequestOrRequestUri(
        joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidIss(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidAud(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidJti(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidExp(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfMissingScopeWhenRequired(
        joseContext, authorizationServerConfiguration, clientConfiguration);
  }

  /**
   * JAR (RFC 9101) Section 6.2:
   *
   * <p>"request and request_uri parameters MUST NOT be included in Request Objects."
   *
   * <p>Including these parameters inside the Request Object JWT is circular and invalid.
   */
  default void throwExceptionIfContainsRequestOrRequestUri(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (claims.getValue("request") != null && !claims.getValue("request").isEmpty()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object must not contain request parameter (JAR Section 6.2)");
    }
    if (claims.getValue("request_uri") != null && !claims.getValue("request_uri").isEmpty()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object must not contain request_uri parameter (JAR Section 6.2)");
    }
  }

  default void throwExceptionIfSymmetricKey(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    if (joseContext.isSymmetricKey()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, request object must signed with asymmetric key");
    }
  }

  default void throwExceptionIfInvalidIss(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains iss claim in jwt payload");
    }
    if (!claims.getIss().equals(clientConfiguration.clientIdValue())
        && !claims.getIss().equals(clientConfiguration.clientIdAlias())) {
      throw new RequestObjectInvalidException(
          "invalid_request_object", "request object is invalid, iss claim must be client_id");
    }
  }

  default void throwExceptionIfInvalidAud(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains aud claim in jwt payload");
    }
    if (claims.getAud().contains(authorizationServerConfiguration.tokenIssuer().value())) {
      return;
    }
    throw new RequestObjectInvalidException(
        "invalid_request_object", "request object is invalid, aud claim must be issuer");
  }

  /**
   * jti claim is OPTIONAL per FAPI 1.0 Advanced (only nbf/exp/aud are required). If present, it can
   * be used for replay detection, but its absence should not cause a rejection.
   */
  default void throwExceptionIfInvalidJti(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    // jti is optional - no validation needed if absent
  }

  /**
   * When require_signed_request_object is true, all authorization request parameters must be
   * included in the signed request object, including scope.
   *
   * <p>FAPI 1.0 Advanced Final, Section 5.2.2 (clause 13): shall require the request object to
   * contain all the authorization request parameters.
   *
   * <p>RFC 9101 (JAR) Section 6.3: The Authorization Server MUST only use the parameters in the
   * Request Object even if the same parameter is provided in the query parameter.
   *
   * @see <a
   *     href="https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server">FAPI
   *     1.0 Advanced Final Section 5.2.2</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9101#section-6.3">RFC 9101 Section 6.3</a>
   */
  default void throwExceptionIfMissingScopeWhenRequired(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    if (!authorizationServerConfiguration.requireSignedRequestObject()) {
      return;
    }
    JsonWebTokenClaims claims = joseContext.claims();
    String scope = claims.getValue("scope");
    if (scope == null || scope.isEmpty()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, when require_signed_request_object is true, scope must be included in the request object");
    }
  }

  default void throwExceptionIfInvalidExp(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasExp()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains exp claim in jwt payload");
    }
    Date date = new Date(SystemDateTime.currentEpochMilliSecond());
    if (claims.getExp().before(date)) {
      throw new RequestObjectInvalidException(
          "invalid_request_object", "request object is invalid, jwt is expired");
    }
  }
}
