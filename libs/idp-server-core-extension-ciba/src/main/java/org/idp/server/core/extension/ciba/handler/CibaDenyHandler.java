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

import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.grant.CibaGrantStatus;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyRequest;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.type.extension.DeniedScopes;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaDenyHandler {

  CibaGrantRepository cibaGrantRepository;
  BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public CibaDenyHandler(
      CibaGrantRepository cibaGrantRepository,
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.backchannelAuthenticationRequestRepository = backchannelAuthenticationRequestRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public CibaDenyResponse handle(CibaDenyRequest request) {
    BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
        request.backchannelAuthenticationRequestIdentifier();
    Tenant tenant = request.tenant();
    authorizationServerConfigurationQueryRepository.get(tenant);

    CibaGrant cibaGrant =
        cibaGrantRepository.get(tenant, backchannelAuthenticationRequestIdentifier);
    CibaGrant updated =
        cibaGrant.updateWith(
            CibaGrantStatus.access_denied, new Authentication(), new DeniedScopes());
    cibaGrantRepository.update(tenant, updated);

    return new CibaDenyResponse(CibaDenyStatus.OK);
  }
}
