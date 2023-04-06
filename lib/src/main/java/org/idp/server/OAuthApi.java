package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrantCreator;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.oauth.validator.OAuthRequestInitialValidator;
import org.idp.server.core.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.core.type.ClientId;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.TokenIssuer;
import org.idp.server.handler.OAuthRequestContextHandler;
import org.idp.server.handler.OAuthAuthorizeHandler;
import org.idp.server.io.OAuthAuthorizeRequest;
import org.idp.server.io.OAuthAuthorizeResponse;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.io.status.OAuthAuthorizeStatus;
import org.idp.server.io.status.OAuthRequestStatus;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestInitialValidator initialValidator = new OAuthRequestInitialValidator();
  OAuthRequestContextHandler requestContextHandler = new OAuthRequestContextHandler();
  OAuthRequestVerifier oAuthRequestVerifier = new OAuthRequestVerifier();

  OAuthAuthorizeHandler authAuthorizeHandler = new OAuthAuthorizeHandler();
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(
      AuthorizationRequestRepository authorizationRequestRepository,
      AuthorizationCodeGrantRepository authorizationCodeGrantRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.authorizationCodeGrantRepository = authorizationCodeGrantRepository;
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
          clientConfigurationRepository.get(tokenIssuer, oAuthRequestParameters.clientId());

      OAuthRequestContext oAuthRequestContext =
          requestContextHandler.handle(
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
      AuthorizationRequest authorizationRequest =
          authorizationRequestRepository.get(authorizationRequestIdentifier);
      TokenIssuer tokenIssuer = authorizationRequest.tokenIssuer();
      ClientId clientId = authorizationRequest.clientId();
      ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
      ClientConfiguration clientConfiguration =
          clientConfigurationRepository.get(tokenIssuer, clientId);
      OAuthAuthorizeContext oAuthAuthorizeContext =
          new OAuthAuthorizeContext(
              authorizationRequest,
              request.user(),
              request.toCustomProperties(),
              serverConfiguration,
              clientConfiguration);
      AuthorizationResponse authorizationResponse =
          authAuthorizeHandler.handle(oAuthAuthorizeContext);

      if (authorizationResponse.hasAuthorizationCode()) {
        AuthorizationCodeGrant authorizationCodeGrant =
            AuthorizationCodeGrantCreator.create(oAuthAuthorizeContext, authorizationResponse);
        authorizationCodeGrantRepository.register(authorizationCodeGrant);
      }

      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, authorizationResponse);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthAuthorizeResponse();
    }
  }
}
