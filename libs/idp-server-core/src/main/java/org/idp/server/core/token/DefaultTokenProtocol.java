package org.idp.server.core.token;

import java.util.Map;
import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.token.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.token.handler.token.TokenRequestHandler;
import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionRequestStatus;
import org.idp.server.core.token.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.core.token.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationRequestStatus;
import org.idp.server.core.token.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.tokenintrospection.TokenIntrospectionContentsCreator;
import org.idp.server.core.token.tokenintrospection.exception.TokenInvalidException;

public class DefaultTokenProtocol implements TokenProtocol {

  TokenRequestHandler tokenRequestHandler;
  TokenIntrospectionHandler tokenIntrospectionHandler;
  TokenRevocationHandler tokenRevocationHandler;
  TokenRequestErrorHandler errorHandler;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;
  LoggerWrapper log = LoggerWrapper.getLogger(DefaultTokenProtocol.class);

  public DefaultTokenProtocol(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      AuthorizationGrantedRepository authorizationGrantedRepository,
      BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository,
      CibaGrantRepository cibaGrantRepository,
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate) {
    this.tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository,
            backchannelAuthenticationRequestRepository,
            cibaGrantRepository,
            oAuthTokenRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.errorHandler = new TokenRequestErrorHandler();
    this.tokenIntrospectionHandler = new TokenIntrospectionHandler(oAuthTokenRepository);
    this.tokenRevocationHandler =
        new TokenRevocationHandler(
            oAuthTokenRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      return tokenRequestHandler.handle(tokenRequest, passwordCredentialsGrantDelegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public TokenIntrospectionResponse inspect(TokenIntrospectionRequest request) {
    try {

      return tokenIntrospectionHandler.handle(request);
    } catch (TokenInvalidException exception) {
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(
          TokenIntrospectionRequestStatus.INVALID_TOKEN, contents);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      Map<String, Object> contents = TokenIntrospectionContentsCreator.createFailureContents();
      return new TokenIntrospectionResponse(TokenIntrospectionRequestStatus.SERVER_ERROR, contents);
    }
  }

  public TokenRevocationResponse revoke(TokenRevocationRequest request) {
    try {

      return tokenRevocationHandler.handle(request);
    } catch (Exception exception) {
      log.error(exception.getMessage(), exception);
      return new TokenRevocationResponse(
          TokenRevocationRequestStatus.SERVER_ERROR, new OAuthToken(), Map.of());
    }
  }
}
