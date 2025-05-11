package org.idp.server.core.ciba.handler;

import org.idp.server.core.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.ciba.grant.CibaGrant;
import org.idp.server.core.ciba.grant.CibaGrantStatus;
import org.idp.server.core.ciba.handler.io.CibaAuthorizeRequest;
import org.idp.server.core.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.ciba.handler.io.CibaAuthorizeStatus;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class CibaAuthorizeHandler {

  CibaGrantRepository cibaGrantRepository;
  ClientNotificationService clientNotificationService;
  AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public CibaAuthorizeHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ClientNotificationGateway clientNotificationGateway,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.clientNotificationService =
        new ClientNotificationService(
            backchannelAuthenticationRequestRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            clientNotificationGateway);
    this.authorizationServerConfigurationRepository = authorizationServerConfigurationRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public CibaAuthorizeResponse handle(CibaAuthorizeRequest request) {
    BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
        request.backchannleAuthenticationIdentifier();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationRepository.get(tenant);
    CibaGrant cibaGrant =
        cibaGrantRepository.get(tenant, backchannelAuthenticationRequestIdentifier);

    // TODO verify

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(
            tenant, cibaGrant.authorizationGrant().clientIdentifier());
    CibaGrant updated = cibaGrant.update(CibaGrantStatus.authorized);
    cibaGrantRepository.update(tenant, updated);

    clientNotificationService.notify(
        tenant, cibaGrant, authorizationServerConfiguration, clientConfiguration);
    return new CibaAuthorizeResponse(CibaAuthorizeStatus.OK);
  }
}
