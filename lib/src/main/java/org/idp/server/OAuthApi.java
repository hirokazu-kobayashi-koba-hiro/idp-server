package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.oauth.OAuthRequestAnalyzer;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.core.oauth.validator.OAuthRequestInitialValidator;
import org.idp.server.core.oauth.verifier.OAuthRequestVerifier;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.OAuthRequestRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.status.OAuthRequestStatus;
import org.idp.server.core.type.TokenIssuer;
import org.idp.server.io.OAuthAuthorizeRequest;
import org.idp.server.io.OAuthAuthorizeResponse;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.service.OAuthRequestContextServiceRegistry;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestInitialValidator initialValidator = new OAuthRequestInitialValidator();
  OAuthRequestAnalyzer requestAnalyzer = new OAuthRequestAnalyzer();
  OAuthRequestContextServiceRegistry contextCreatorRegistry =
      new OAuthRequestContextServiceRegistry();
  OAuthRequestVerifier oAuthRequestVerifier = new OAuthRequestVerifier();
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  OAuthRequestRepository oAuthRequestRepository;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(
      OAuthRequestRepository oAuthRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthRequestRepository = oAuthRequestRepository;
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
      OAuthRequestPattern oAuthRequestPattern =
          requestAnalyzer.analyzePattern(oAuthRequestParameters);
      OAuthRequestContextService oAuthRequestContextService =
          contextCreatorRegistry.get(oAuthRequestPattern);
      OAuthRequestContext oAuthRequestContext =
          oAuthRequestContextService.create(
              oAuthRequestParameters, serverConfiguration, clientConfiguration);

      oAuthRequestVerifier.verify(oAuthRequestContext);

      oAuthRequestRepository.register(oAuthRequestContext);

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

  public OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest oAuthAuthorizeRequest) {
    return new OAuthAuthorizeResponse();
  }
}
