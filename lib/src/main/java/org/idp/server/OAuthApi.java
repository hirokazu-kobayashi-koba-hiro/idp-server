package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.io.OAuthAuthorizeRequest;
import org.idp.server.handler.io.OAuthAuthorizeResponse;
import org.idp.server.handler.io.OAuthRequest;
import org.idp.server.handler.io.OAuthRequestResponse;
import org.idp.server.handler.io.status.OAuthAuthorizeStatus;
import org.idp.server.handler.io.status.OAuthRequestStatus;
import org.idp.server.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.handler.oauth.OAuthRequestExceptionHandler;
import org.idp.server.handler.oauth.OAuthRequestHandler;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.response.AuthorizationResponse;
import org.idp.server.oauth.validator.OAuthRequestValidator;
import org.idp.server.type.OAuthRequestParameters;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthApi */
public class OAuthApi {
  OAuthRequestValidator requestValidator;
  OAuthRequestHandler requestHandler;
  OAuthRequestExceptionHandler oAuthRequestExceptionHandler;
  OAuthAuthorizeHandler authAuthorizeHandler;
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  OAuthApi(OAuthRequestHandler requestHandler, OAuthAuthorizeHandler authAuthorizeHandler) {
    this.requestValidator = new OAuthRequestValidator();
    this.requestHandler = requestHandler;
    this.oAuthRequestExceptionHandler = new OAuthRequestExceptionHandler();
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
      return new OAuthRequestResponse(
          OAuthRequestStatus.BAD_REQUEST, exception.error(), exception.errorDescription());
    } catch (OAuthRedirectableBadRequestException exception) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return oAuthRequestExceptionHandler.handle(exception);
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
