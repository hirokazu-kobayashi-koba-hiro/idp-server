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


package org.idp.server.core.oidc.factory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.oidc.ClaimsValue;
import org.idp.server.basic.type.rar.AuthorizationDetailsEntity;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.id_token.RequestedClaimsPayload;
import org.idp.server.core.oidc.rar.AuthorizationDetail;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
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
    try {
      Object object = authorizationDetailsEntity.value();
      if (object instanceof String string) {
        JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
        List list = jsonConverter.read(string, List.class);
        List<Map> details = (List<Map>) list;
        List<AuthorizationDetail> authorizationDetailsList =
            details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
        return new AuthorizationDetails(authorizationDetailsList);
      }
      List<Map> details = (List<Map>) authorizationDetailsEntity.value();
      List<AuthorizationDetail> authorizationDetailsList =
          details.stream().map(detail -> new AuthorizationDetail(detail)).toList();
      return new AuthorizationDetails(authorizationDetailsList);
    } catch (Exception exception) {
      return new AuthorizationDetails();
    }
  }
}
