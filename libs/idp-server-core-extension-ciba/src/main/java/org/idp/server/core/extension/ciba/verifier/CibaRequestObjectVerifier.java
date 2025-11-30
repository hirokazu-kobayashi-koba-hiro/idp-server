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

package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationUnauthorizedException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.RequestObjectInvalidException;
import org.idp.server.core.openid.oauth.verifier.extension.RequestObjectVerifyable;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

public class CibaRequestObjectVerifier implements CibaExtensionVerifier, RequestObjectVerifyable {

  @Override
  public boolean shouldVerify(CibaRequestContext context) {
    return context.isRequestObjectPattern();
  }

  public void verify(CibaRequestContext context) {
    try {
      verify(context.joseContext(), context.serverConfiguration(), context.clientConfiguration());
    } catch (RequestObjectInvalidException exception) {
      // CIBA Core Section 13 does not define 'invalid_request_object' error code
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", exception.getMessage());
    }
  }

  /**
   * Overrides the default iss validation to return CIBA-specific error.
   *
   * <p>Per CIBA Core Section 13, a mismatch between client_id and iss represents a client
   * authentication failure, which should return 'invalid_client' (HTTP 401), not
   * 'invalid_request_object' (HTTP 400).
   *
   * <p>Note: CIBA does not define 'invalid_request_object' error code, so missing iss returns
   * 'invalid_request', while iss mismatch returns 'invalid_client'.
   */
  @Override
  public void throwExceptionIfInvalidIss(
      JoseContext joseContext,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new RequestObjectInvalidException(
          "invalid_request", "request object is invalid, must contains iss claim in jwt payload");
    }
    if (!claims.getIss().equals(clientConfiguration.clientIdValue())
        && !claims.getIss().equals(clientConfiguration.clientIdAlias())) {
      // CIBA: iss mismatch is client authentication failure (invalid_client, 401)
      throw new BackchannelAuthenticationUnauthorizedException(
          "invalid_client",
          "Client authentication failed: 'iss' claim in request object does not match client_id.");
    }
  }
}
