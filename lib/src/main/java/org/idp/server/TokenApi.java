package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.core.type.ClientId;
import org.idp.server.core.type.TokenIssuer;
import org.idp.server.core.type.TokenRequestParameters;
import org.idp.server.io.TokenRequest;
import org.idp.server.io.TokenRequestResponse;

public class TokenApi {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  Logger log = Logger.getLogger(TokenApi.class.getName());

  TokenApi(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    TokenIssuer tokenIssuer = tokenRequest.toTokenIssuer();
    TokenRequestParameters parameters = tokenRequest.toParameters();
    try {
      ClientId clientId = parameters.clientId();
      ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
      ClientConfiguration clientConfiguration =
          clientConfigurationRepository.get(tokenIssuer, clientId);
      // TODO validate
      AuthorizationCodeGrant authorizationCodeGrant =
          authorizationCodeGrantRepository.find(parameters.code());
      // TODO verify

      return new TokenRequestResponse();
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new TokenRequestResponse();
    }
  }
}
