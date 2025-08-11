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

package org.idp.server.core.openid.oauth.factory;

import java.util.Set;
import java.util.UUID;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.type.oidc.ClaimsValue;
import org.idp.server.core.openid.oauth.type.rar.AuthorizationDetailsEntity;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** AuthorizationRequestFactory */
public interface AuthorizationRequestFactory {
  AuthorizationRequest create(
      Tenant tenant,
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);

  default AuthorizationRequestIdentifier createIdentifier() {
    return new AuthorizationRequestIdentifier(UUID.randomUUID().toString());
  }

  default RequestedClaimsPayload convertClaimsPayload(ClaimsValue claimsValue) {
    try {
      JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
      return claimsValue.exists()
          ? jsonConverter.read(claimsValue.value(), RequestedClaimsPayload.class)
          : new RequestedClaimsPayload();
    } catch (Exception exception) {
      return new RequestedClaimsPayload();
    }
  }

  default AuthorizationDetails convertAuthorizationDetails(
      AuthorizationDetailsEntity authorizationDetailsEntity) {
    if (!authorizationDetailsEntity.exists()) {
      return new AuthorizationDetails();
    }

    if (authorizationDetailsEntity.isString()) {
      Object object = authorizationDetailsEntity.value();
      return AuthorizationDetails.fromString(object.toString());
    }

    Object object = authorizationDetailsEntity.value();
    return AuthorizationDetails.fromObject(object);
  }
}
