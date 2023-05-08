package org.idp.server.handler.ciba;

import org.idp.server.ciba.gateway.ClientNotificationGateway;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.grant.CibaGrantStatus;
import org.idp.server.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.ciba.repository.CibaGrantRepository;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.handler.ciba.io.CibaAuthorizeRequest;
import org.idp.server.handler.ciba.io.CibaAuthorizeResponse;
import org.idp.server.handler.ciba.io.CibaAuthorizeStatus;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.oauth.TokenIssuer;

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
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    CibaGrant cibaGrant = cibaGrantRepository.find(authReqId);

    // TODO verify

    ClientConfiguration clientConfiguration =
        clientConfigurationRepository.get(tokenIssuer, cibaGrant.authorizationGrant().clientId());
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.authorized);
    cibaGrantRepository.update(updated);

    clientNotificationService.notify(cibaGrant, serverConfiguration, clientConfiguration);
    return new CibaAuthorizeResponse(CibaAuthorizeStatus.OK);
  }
}
