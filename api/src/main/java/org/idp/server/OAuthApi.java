package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.oauth.OAuthBadRequestException;
import org.idp.server.core.oauth.OAuthRequestAnalyzer;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.OAuthRequestValidatorProvider;
import org.idp.server.core.oauth.params.OAuthRequestParameters;
import org.idp.server.core.oauth.params.OAuthRequestResult;
import org.idp.server.core.oauth.params.TokenIssuer;
import org.idp.server.core.oauth.validator.OAuthRequestInitialValidator;
import org.idp.server.core.oauth.validator.OAuthRequestValidator;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;

/** OAuthApi */
public class OAuthApi {

  OAuthRequestInitialValidator initialValidator = new OAuthRequestInitialValidator();
  OAuthRequestAnalyzer requestAnalyzer = new OAuthRequestAnalyzer();
  OAuthRequestValidatorProvider validatorProvider = new OAuthRequestValidatorProvider();
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {
    OAuthRequestParameters oAuthRequestParameters = oAuthRequest.toParameters();
    TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
    try {
      initialValidator.validate(oAuthRequestParameters);
      OAuthRequestPattern oAuthRequestPattern =
          requestAnalyzer.analyzePattern(oAuthRequestParameters);
      OAuthRequestValidator oAuthRequestValidator = validatorProvider.provide(oAuthRequestPattern);

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
