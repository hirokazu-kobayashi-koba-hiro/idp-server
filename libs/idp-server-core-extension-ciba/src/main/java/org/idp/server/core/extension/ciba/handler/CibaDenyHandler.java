package org.idp.server.core.extension.ciba.handler;

import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.grant.CibaGrantStatus;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyRequest;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaDenyStatus;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaDenyHandler {

  CibaGrantRepository cibaGrantRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public CibaDenyHandler(
      CibaGrantRepository cibaGrantRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
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
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.access_denied);
    cibaGrantRepository.update(tenant, updated);

    return new CibaDenyResponse(CibaDenyStatus.OK);
  }
}
