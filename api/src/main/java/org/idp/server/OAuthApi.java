package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.oauth.OAuthBadRequestException;
import org.idp.server.oauth.OAuthRequestAnalyzer;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.request.OAuthRequestContext;
import org.idp.server.oauth.request.OAuthRequestContextService;
import org.idp.server.oauth.validator.OAuthRequestInitialValidator;
import org.idp.server.repository.ClientConfigurationRepository;
import org.idp.server.repository.ServerConfigurationRepository;
import org.idp.server.service.OAuthRequestContextServiceRegistry;
import org.idp.server.type.ClientId;
import org.idp.server.type.OAuthRequestParameters;
import org.idp.server.type.OAuthRequestResult;
import org.idp.server.type.TokenIssuer;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestInitialValidator initialValidator = new OAuthRequestInitialValidator();
  OAuthRequestAnalyzer requestAnalyzer = new OAuthRequestAnalyzer();
  OAuthRequestContextServiceRegistry contextCreatorRegistry =
      new OAuthRequestContextServiceRegistry();
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
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
          clientConfigurationRepository.get(new ClientId(oAuthRequestParameters.clientId()));
      OAuthRequestPattern oAuthRequestPattern =
          requestAnalyzer.analyzePattern(oAuthRequestParameters);
      OAuthRequestContextService oAuthRequestContextService =
          contextCreatorRegistry.get(oAuthRequestPattern);
      OAuthRequestContext oAuthRequestContext =
          oAuthRequestContextService.create(
              oAuthRequestParameters, serverConfiguration, clientConfiguration);

      return new OAuthRequestResponse();
    } catch (OAuthBadRequestException exception) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestResult.BAD_REQUEST);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthRequestResponse(OAuthRequestResult.SERVER_ERROR);
    }
  }
}
