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

package org.idp.server.core.extension.ciba.grant;

import java.time.LocalDateTime;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.ciba.Interval;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.client.Client;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.grant.AuthorizationGrantBuilder;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class CibaGrantFactory {

  CibaRequestContext context;
  BackchannelAuthenticationResponse response;
  User user;
  Authentication authentication;

  public CibaGrantFactory(
      CibaRequestContext context,
      BackchannelAuthenticationResponse response,
      User user,
      Authentication authentication) {
    this.context = context;
    this.response = response;
    this.user = user;
    this.authentication = authentication;
  }

  public CibaGrant create() {

    BackchannelAuthenticationRequestIdentifier identifier =
        context.backchannelAuthenticationRequestIdentifier();
    RequestedClientId requestedClientId = context.requestedClientId();

    TenantIdentifier tenantIdentifier = context.tenantIdentifier();
    Client client = context.client();
    Scopes scopes = context.scopes();
    // TODO authorization_details
    AuthorizationGrantBuilder builder =
        new AuthorizationGrantBuilder(tenantIdentifier, requestedClientId, GrantType.ciba, scopes)
            .add(client)
            .add(user)
            .add(authentication);

    if (user.hasCustomProperties()) {
      builder.add(user.customProperties());
    }

    AuthorizationGrant authorizationGrant = builder.build();
    AuthReqId authReqId = response.authReqId();
    LocalDateTime now = SystemDateTime.now();
    ExpiredAt expiredAt = new ExpiredAt(now.plusSeconds(context.expiresIn().value()));
    Interval interval = context.interval();

    return new CibaGrant(
        identifier,
        authorizationGrant,
        authReqId,
        expiredAt,
        interval,
        CibaGrantStatus.authorization_pending);
  }
}
