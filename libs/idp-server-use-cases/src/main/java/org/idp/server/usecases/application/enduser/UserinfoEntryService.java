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


package org.idp.server.usecases.application.enduser;

import org.idp.server.basic.type.oauth.Subject;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.token.TokenEventPublisher;
import org.idp.server.core.oidc.userinfo.UserinfoApi;
import org.idp.server.core.oidc.userinfo.UserinfoProtocol;
import org.idp.server.core.oidc.userinfo.UserinfoProtocols;
import org.idp.server.core.oidc.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction(readOnly = true)
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {

  UserinfoProtocols userinfoProtocols;
  UserQueryRepository userQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  TokenEventPublisher eventPublisher;

  public UserinfoEntryService(
      UserinfoProtocols userinfoProtocols,
      UserQueryRepository userQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      TokenEventPublisher eventPublisher) {
    this.userinfoProtocols = userinfoProtocols;
    this.userQueryRepository = userQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public User findUser(Tenant tenant, Subject subject) {
    UserIdentifier userIdentifier = new UserIdentifier(subject.value());
    return userQueryRepository.get(tenant, userIdentifier);
  }

  public UserinfoRequestResponse request(
      TenantIdentifier tenantIdentifier,
      String authorizationHeader,
      String clientCert,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    UserinfoRequest userinfoRequest = new UserinfoRequest(tenant, authorizationHeader);
    userinfoRequest.setClientCert(clientCert);

    UserinfoProtocol userinfoProtocol = userinfoProtocols.get(tenant.authorizationProvider());

    UserinfoRequestResponse result = userinfoProtocol.request(userinfoRequest, this);

    if (result.isOK()) {
      eventPublisher.publish(
          tenant,
          result.oAuthToken(),
          DefaultSecurityEventType.userinfo_success,
          requestAttributes);
    }

    return result;
  }
}
