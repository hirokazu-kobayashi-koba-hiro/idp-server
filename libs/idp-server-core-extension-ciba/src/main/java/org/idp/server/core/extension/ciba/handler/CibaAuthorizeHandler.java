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

package org.idp.server.core.extension.ciba.handler;

import java.util.UUID;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.grant.CibaGrantStatus;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeRequest;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.extension.ciba.validator.CibaAuthorizeRequestValidator;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaAuthorizeHandler {

  CibaGrantRepository cibaGrantRepository;
  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  ClientNotificationService clientNotificationService;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuthorizationGrantedRepository authorizationGrantedRepository;

  public CibaAuthorizeHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      ClientNotificationService clientNotificationService,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.clientNotificationService = clientNotificationService;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.authorizationGrantedRepository = authorizationGrantedRepository;
  }

  public CibaAuthorizeResponse handle(CibaAuthorizeRequest request) {
    BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
        request.backchannleAuthenticationIdentifier();
    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        backchannelAuthenticationRequestRepository.find(
            request.tenant(), backchannelAuthenticationRequestIdentifier);

    CibaAuthorizeRequestValidator validator =
        new CibaAuthorizeRequestValidator(
            request.backchannleAuthenticationIdentifier(), request.authentication());
    validator.validate();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    CibaGrant cibaGrant =
        cibaGrantRepository.get(tenant, backchannelAuthenticationRequestIdentifier);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(
            tenant, cibaGrant.authorizationGrant().clientIdentifier());
    CibaGrant updated =
        cibaGrant.updateWith(
            CibaGrantStatus.authorized, request.authentication(), request.toDeniedScopes());
    cibaGrantRepository.update(tenant, updated);

    // Register AuthorizationGranted at CIBA authorize time
    registerOrUpdateAuthorizationGranted(tenant, updated.authorizationGrant());

    clientNotificationService.notify(
        tenant,
        backchannelAuthenticationRequest,
        cibaGrant,
        authorizationServerConfiguration,
        clientConfiguration);
    return new CibaAuthorizeResponse(CibaAuthorizeStatus.OK);
  }

  private void registerOrUpdateAuthorizationGranted(
      Tenant tenant, AuthorizationGrant authorizationGrant) {
    AuthorizationGranted latest =
        authorizationGrantedRepository.find(
            tenant, authorizationGrant.requestedClientId(), authorizationGrant.user());

    if (latest.exists()) {
      AuthorizationGranted merge = latest.merge(authorizationGrant);
      authorizationGrantedRepository.update(tenant, merge);
      return;
    }
    AuthorizationGrantedIdentifier authorizationGrantedIdentifier =
        new AuthorizationGrantedIdentifier(UUID.randomUUID().toString());
    AuthorizationGranted authorizationGranted =
        new AuthorizationGranted(authorizationGrantedIdentifier, authorizationGrant);
    authorizationGrantedRepository.register(tenant, authorizationGranted);
  }
}
