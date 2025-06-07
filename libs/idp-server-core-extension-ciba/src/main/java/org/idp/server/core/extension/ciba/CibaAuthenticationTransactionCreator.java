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

package org.idp.server.core.extension.ciba;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.basic.type.AuthFlow;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.extension.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaAuthenticationTransactionCreator {

  public static AuthenticationTransaction create(
      Tenant tenant, CibaIssueResponse cibaIssueResponse) {

    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString());
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(
            cibaIssueResponse.backchannelAuthenticationRequestIdentifier().value());

    AuthenticationRequest authenticationRequest =
        toAuthenticationRequest(tenant, cibaIssueResponse);
    AuthenticationPolicy authenticationPolicy =
        cibaIssueResponse.findSatisfiedAuthenticationPolicy();

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("auth_req_id", cibaIssueResponse.authReqId().value());
    AuthenticationTransactionAttributes authenticationTransactionAttributes =
        new AuthenticationTransactionAttributes(attributes);

    return new AuthenticationTransaction(
        identifier,
        authorizationIdentifier,
        authenticationRequest,
        authenticationPolicy,
        authenticationTransactionAttributes);
  }

  private static AuthenticationRequest toAuthenticationRequest(
      Tenant tenant, CibaIssueResponse cibaIssueResponse) {
    BackchannelAuthenticationRequest backchannelAuthenticationRequest = cibaIssueResponse.request();
    ExpiresIn expiresIn = cibaIssueResponse.expiresIn();
    AuthFlow authFlow = AuthFlow.CIBA;
    TenantIdentifier tenantIdentifier = tenant.identifier();

    RequestedClientId requestedClientId = backchannelAuthenticationRequest.requestedClientId();
    User user = cibaIssueResponse.user();
    AuthorizationDetails authorizationDetails = cibaIssueResponse.request().authorizationDetails();
    AuthenticationContext context =
        new AuthenticationContext(
            cibaIssueResponse.acrValues(), cibaIssueResponse.scopes(), authorizationDetails);

    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(expiresIn.value());
    return new AuthenticationRequest(
        authFlow, tenantIdentifier, requestedClientId, user, context, createdAt, expiredAt);
  }
}
