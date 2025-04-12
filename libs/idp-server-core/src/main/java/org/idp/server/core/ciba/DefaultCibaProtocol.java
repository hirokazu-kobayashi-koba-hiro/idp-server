package org.idp.server.core.ciba;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.basic.dependency.protcol.DefaultAuthorizationProvider;
import org.idp.server.core.ciba.clientnotification.NotificationClient;
import org.idp.server.core.ciba.handler.CibaAuthorizeHandler;
import org.idp.server.core.ciba.handler.CibaDenyHandler;
import org.idp.server.core.ciba.handler.CibaRequestErrorHandler;
import org.idp.server.core.ciba.handler.CibaRequestHandler;
import org.idp.server.core.ciba.handler.io.*;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultCibaProtocol implements CibaProtocol {

  CibaRequestHandler cibaRequestHandler;
  CibaAuthorizeHandler cibaAuthorizeHandler;
  CibaDenyHandler cibaDenyHandler;
  CibaRequestErrorHandler errorHandler;
  Logger log = Logger.getLogger(DefaultCibaProtocol.class.getName());

  public DefaultCibaProtocol(
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.cibaRequestHandler =
        new CibaRequestHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.cibaAuthorizeHandler =
        new CibaAuthorizeHandler(
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            new NotificationClient(),
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.cibaDenyHandler =
        new CibaDenyHandler(
            cibaGrantRepository, serverConfigurationRepository, clientConfigurationRepository);
    ;
    this.errorHandler = new CibaRequestErrorHandler();
  }

  @Override
  public AuthorizationProtocolProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public CibaRequestResponse request(CibaRequest request, CibaRequestDelegate delegate) {
    try {
      return cibaRequestHandler.handle(request, delegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public CibaAuthorizeResponse authorize(CibaAuthorizeRequest request) {
    try {
      return cibaAuthorizeHandler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaAuthorizeResponse(CibaAuthorizeStatus.SERVER_ERROR);
    }
  }

  public CibaDenyResponse deny(CibaDenyRequest request) {
    try {
      return cibaDenyHandler.handle(request);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new CibaDenyResponse(CibaDenyStatus.SERVER_ERROR);
    }
  }
}
