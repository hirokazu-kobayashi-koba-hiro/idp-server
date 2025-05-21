package org.idp.server.core.extension.ciba.handler;

import org.idp.server.core.extension.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.grant.CibaGrantStatus;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeRequest;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeResponse;
import org.idp.server.core.extension.ciba.handler.io.CibaAuthorizeStatus;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaAuthorizeHandler {

  CibaGrantRepository cibaGrantRepository;
  ClientNotificationService clientNotificationService;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public CibaAuthorizeHandler(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ClientNotificationGateway clientNotificationGateway,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.cibaGrantRepository = cibaGrantRepository;
    this.clientNotificationService =
        new ClientNotificationService(
            backchannelAuthenticationRequestRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            clientNotificationGateway);
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public CibaAuthorizeResponse handle(CibaAuthorizeRequest request) {
    BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
        request.backchannleAuthenticationIdentifier();

    Tenant tenant = request.tenant();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
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
