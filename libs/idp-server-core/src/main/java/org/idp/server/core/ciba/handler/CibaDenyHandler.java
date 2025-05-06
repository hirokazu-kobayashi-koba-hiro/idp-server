package org.idp.server.core.ciba.handler;

import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantStatus;
import org.idp.server.core.ciba.handler.io.CibaDenyRequest;
import org.idp.server.core.ciba.handler.io.CibaDenyResponse;
import org.idp.server.core.ciba.handler.io.CibaDenyStatus;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationRepository;

public class CibaDenyHandler {

  CibaGrantRepository cibaGrantRepository;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public CibaDenyHandler(
      CibaGrantRepository cibaGrantRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CibaDenyResponse handle(CibaDenyRequest request) {
    BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
        request.backchannelAuthenticationRequestIdentifier();
    Tenant tenant = request.tenant();
    authorizationServerConfigurationRepository.get(tenant);

    CibaGrant cibaGrant =
        cibaGrantRepository.get(tenant, backchannelAuthenticationRequestIdentifier);
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.access_denied);
    cibaGrantRepository.update(tenant, updated);

    return new CibaDenyResponse(CibaDenyStatus.OK);
  }
}
