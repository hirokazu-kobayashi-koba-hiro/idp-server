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
import org.idp.server.core.extension.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.openid.authentication.*;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.oauth.configuration.client.ClientAttributes;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.StandardAuthFlow;
import org.idp.server.core.openid.oauth.type.oauth.ExpiresIn;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaAuthenticationTransactionCreator {

  public static AuthenticationTransaction create(
      Tenant tenant,
      CibaIssueResponse cibaIssueResponse,
      AuthenticationPolicyConfiguration policyConfiguration) {

    AuthenticationTransactionIdentifier identifier =
        new AuthenticationTransactionIdentifier(UUID.randomUUID().toString());
    AuthorizationIdentifier authorizationIdentifier =
        new AuthorizationIdentifier(
            cibaIssueResponse.backchannelAuthenticationRequestIdentifier().value());

    AuthenticationRequest authenticationRequest =
        toAuthenticationRequest(tenant, cibaIssueResponse);
    AuthenticationPolicy authenticationPolicy =
        policyConfiguration.findSatisfiedAuthenticationPolicy(
            authenticationRequest.requestedClientId(),
            authenticationRequest.acrValues(),
            authenticationRequest.scopes());
    ;

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
    StandardAuthFlow standardAuthFlow = StandardAuthFlow.CIBA;
    TenantIdentifier tenantIdentifier = tenant.identifier();
    TenantAttributes tenantAttributes = tenant.attributes();

    RequestedClientId requestedClientId = backchannelAuthenticationRequest.requestedClientId();
    ClientAttributes clientAttributes = cibaIssueResponse.clientAttributes();
    User user = cibaIssueResponse.user();
    AuthenticationDevice authenticationDevice =
        extractAuthenticationDevice(backchannelAuthenticationRequest, user);
    AuthorizationDetails authorizationDetails = cibaIssueResponse.request().authorizationDetails();
    AuthenticationContext context =
        new AuthenticationContext(
            cibaIssueResponse.acrValues(),
            cibaIssueResponse.scopes(),
            cibaIssueResponse.bindingMessage(),
            authorizationDetails);

    LocalDateTime createdAt = SystemDateTime.now();
    LocalDateTime expiredAt = createdAt.plusSeconds(expiresIn.value());
    return new AuthenticationRequest(
        standardAuthFlow.toAuthFlow(),
        tenantIdentifier,
        tenantAttributes,
        requestedClientId,
        clientAttributes,
        user,
        authenticationDevice,
        context,
        createdAt,
        expiredAt);
  }

  private static AuthenticationDevice extractAuthenticationDevice(
      BackchannelAuthenticationRequest request, User user) {
    if (!request.hasLoginHint()) {
      return user.findPrimaryAuthenticationDevice();
    }

    return request
        .loginHint()
        .asDeviceIdentifier()
        .map(deviceId -> user.findAuthenticationDevice(deviceId.value()))
        .filter(AuthenticationDevice::exists)
        .orElseGet(user::findPrimaryAuthenticationDevice);
  }
}
