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

package org.idp.server.core.oidc.verifier.extension;

import java.util.Date;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.exception.RequestObjectInvalidException;
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
    throwExceptionIfInvalidIss(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidAud(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidJti(joseContext, authorizationServerConfiguration, clientConfiguration);
    throwExceptionIfInvalidExp(joseContext, authorizationServerConfiguration, clientConfiguration);
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

  default void throwExceptionIfInvalidJti(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasJti()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains jti claim in jwt payload");
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
    Date date = new Date(SystemDateTime.epochMilliSecond());
    if (claims.getExp().before(date)) {
      throw new RequestObjectInvalidException(
          "invalid_request_object", "request object is invalid, jwt is expired");
    }
  }
}
