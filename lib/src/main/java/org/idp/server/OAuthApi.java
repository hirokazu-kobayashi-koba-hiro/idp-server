package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.validator.OAuthRequestInitialValidator;
import org.idp.server.core.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.core.type.ClientId;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.status.OAuthRequestStatus;
import org.idp.server.core.type.TokenIssuer;
import org.idp.server.io.OAuthAuthorizeRequest;
import org.idp.server.io.OAuthAuthorizeResponse;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.handler.oauth.OAuthRequestContextHandler;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestInitialValidator initialValidator = new OAuthRequestInitialValidator();
  OAuthRequestContextHandler contextHandler =
      new OAuthRequestContextHandler();
  OAuthRequestVerifier oAuthRequestVerifier = new OAuthRequestVerifier();
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  AuthorizationRequestRepository authorizationRequestRepository;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {
    OAuthRequestParameters oAuthRequestParameters = oAuthRequest.toParameters();
    TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
    try {
      ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
      initialValidator.validate(oAuthRequestParameters);
      ClientConfiguration clientConfiguration =
          clientConfigurationRepository.get(oAuthRequestParameters.clientId());

      OAuthRequestContext oAuthRequestContext = contextHandler.handle(
              oAuthRequestParameters, serverConfiguration, clientConfiguration);

      oAuthRequestVerifier.verify(oAuthRequestContext);

      authorizationRequestRepository.register(oAuthRequestContext.authorizationRequest());

      return new OAuthRequestResponse(OAuthRequestStatus.OK, oAuthRequestContext);
    } catch (OAuthBadRequestException exception) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST);
    } catch (OAuthRedirectableBadRequestException exception) {
        log.log(Level.WARNING, exception.getMessage(), exception);
        return new OAuthRequestResponse(OAuthRequestStatus.REDIRECABLE_BAD_REQUEST);
    } catch (ServerConfigurationNotFoundException
        | ClientConfigurationNotFoundException exception) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.BAD_REQUEST);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestStatus.SERVER_ERROR);
    }
  }

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request) {
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    try {
      AuthorizationRequest authorizationRequest = authorizationRequestRepository.get(authorizationRequestIdentifier);
      TokenIssuer tokenIssuer = authorizationRequest.tokenIssuer();
      ClientId clientId = authorizationRequest.clientId();
      ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
      ClientConfiguration clientConfiguration = clientConfigurationRepository.get(clientId);

      return new OAuthAuthorizeResponse();
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthAuthorizeResponse();
    }
  }
}
