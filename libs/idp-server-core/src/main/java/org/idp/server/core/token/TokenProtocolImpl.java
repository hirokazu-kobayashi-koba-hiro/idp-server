package org.idp.server.core.token;

import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.token.handler.token.TokenRequestHandler;
import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class TokenProtocolImpl implements TokenProtocol {

  TokenRequestHandler tokenRequestHandler;
  TokenRequestErrorHandler errorHandler;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;

  public TokenProtocolImpl(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository,
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            oAuthTokenRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);
    this.errorHandler = new TokenRequestErrorHandler();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      return tokenRequestHandler.handle(tokenRequest, passwordCredentialsGrantDelegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public void setPasswordCredentialsGrantDelegate(
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate) {
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
  }
}
