package org.idp.server.core.handler.ciba;

import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantStatus;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.ciba.io.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.ciba.AuthReqId;

public class CibaDenyHandler {

  CibaGrantRepository cibaGrantRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public CibaDenyHandler(
      CibaGrantRepository cibaGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CibaDenyResponse handle(CibaDenyRequest request) {
    AuthReqId authReqId = request.toAuthReqId();
    Tenant tenant = request.tenant();
    serverConfigurationRepository.get(tenant.identifier());

    CibaGrant cibaGrant = cibaGrantRepository.find(authReqId);
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.access_denied);
    cibaGrantRepository.update(updated);

    return new CibaDenyResponse(CibaDenyStatus.OK);
  }
}
