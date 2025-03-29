package org.idp.server.core.handler.ciba;

import org.idp.server.core.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantStatus;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.handler.ciba.io.CibaAuthorizeRequest;
import org.idp.server.core.handler.ciba.io.CibaAuthorizeResponse;
import org.idp.server.core.handler.ciba.io.CibaAuthorizeStatus;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.ciba.AuthReqId;

public class CibaAuthorizeHandler {

  CibaGrantRepository cibaGrantRepository;
  ClientNotificationService clientNotificationService;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public CibaAuthorizeHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ClientNotificationGateway clientNotificationGateway,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.clientNotificationService =
        new ClientNotificationService(
            backchannelAuthenticationRequestRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            clientNotificationGateway);
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public CibaAuthorizeResponse handle(CibaAuthorizeRequest request) {
    AuthReqId authReqId = request.toAuthReqId();
    Tenant tenant = request.tenant();
    ServerConfiguration serverConfiguration =
        serverConfigurationRepository.get(tenant.identifier());
    CibaGrant cibaGrant = cibaGrantRepository.find(authReqId);

    // TODO verify

    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(
            tenant, cibaGrant.authorizationGrant().clientIdentifier());
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.authorized);
    cibaGrantRepository.update(updated);

    clientNotificationService.notify(cibaGrant, serverConfiguration, clientConfiguration);
    return new CibaAuthorizeResponse(CibaAuthorizeStatus.OK);
  }
}
