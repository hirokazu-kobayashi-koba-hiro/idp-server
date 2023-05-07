package org.idp.server.handler.ciba;

import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.grant.CibaGrantStatus;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.handler.ciba.io.CibaAuthorizeRequest;
import org.idp.server.handler.ciba.io.CibaAuthorizeResponse;
import org.idp.server.handler.ciba.io.CibaAuthorizeStatus;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.TokenIssuer;

public class CibaAuthorizeHandler {

  CibaGrantRepository cibaGrantRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public CibaAuthorizeHandler(
      CibaGrantRepository cibaGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CibaAuthorizeResponse handle(CibaAuthorizeRequest request) {
    AuthReqId authReqId = request.toAuthReqId();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    serverConfigurationRepository.get(tokenIssuer);
    CibaGrant cibaGrant = cibaGrantRepository.find(authReqId);
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.authorized);
    cibaGrantRepository.update(updated);
    return new CibaAuthorizeResponse(CibaAuthorizeStatus.OK);
  }
}
