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

package org.idp.server.core.openid.token.handler.tokenintrospection;

import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionInternalRequest;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.openid.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.openid.token.tokenintrospection.verifier.TokenIntrospectionVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionInternalHandler {

  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  OAuthTokenQueryRepository oAuthTokenQueryRepository;

  public TokenIntrospectionInternalHandler(
      OAuthTokenCommandRepository oAuthTokenCommandRepository,
      OAuthTokenQueryRepository oAuthTokenQueryRepository) {
    this.oAuthTokenCommandRepository = oAuthTokenCommandRepository;
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
  }

  public TokenIntrospectionResponse handle(TokenIntrospectionInternalRequest request) {

    AccessTokenEntity accessTokenEntity = request.accessToken();
    Tenant tenant = request.tenant();

    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(tenant, accessTokenEntity);
    TokenIntrospectionVerifier verifier = new TokenIntrospectionVerifier(oAuthToken);
    TokenIntrospectionRequestStatus verifiedStatus = verifier.verify();

    if (!verifiedStatus.isOK()) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(verifiedStatus, oAuthToken, contents);
    }

    Map<String, Object> contents =
        TokenIntrospectionContentsCreator.createSuccessContents(oAuthToken);

    if (oAuthToken.isOneshotToken()) {
      oAuthTokenCommandRepository.delete(request.tenant(), oAuthToken);
    }

    return new TokenIntrospectionResponse(verifiedStatus, oAuthToken, contents);
  }
}
