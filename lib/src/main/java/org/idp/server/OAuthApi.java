package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.oauth.validator.OAuthRequestValidator;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.oauth.TokenIssuer;
import org.idp.server.handler.OAuthAuthorizeHandler;
import org.idp.server.handler.OAuthRequestHandler;
import org.idp.server.io.OAuthAuthorizeRequest;
import org.idp.server.io.OAuthAuthorizeResponse;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;
import org.idp.server.io.status.OAuthAuthorizeStatus;
import org.idp.server.io.status.OAuthRequestStatus;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestValidator requestValidator;
  OAuthRequestHandler requestHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(OAuthRequestHandler requestHandler, OAuthAuthorizeHandler authAuthorizeHandler) {
    this.requestValidator = new OAuthRequestValidator();
    this.requestHandler = requestHandler;
    this.authAuthorizeHandler = authAuthorizeHandler;
  }

  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {
    OAuthRequestParameters oAuthRequestParameters = oAuthRequest.toParameters();
    TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
    try {
      requestValidator.validate(oAuthRequestParameters);

      OAuthRequestContext oAuthRequestContext =
          requestHandler.handle(oAuthRequestParameters, tokenIssuer);

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
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    CustomProperties customProperties = request.toCustomProperties();
    try {
      AuthorizationResponse authorizationResponse =
          authAuthorizeHandler.handle(
              tokenIssuer, authorizationRequestIdentifier, user, customProperties);

      return new OAuthAuthorizeResponse(OAuthAuthorizeStatus.OK, authorizationResponse);
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new OAuthAuthorizeResponse();
    }
  }
}
