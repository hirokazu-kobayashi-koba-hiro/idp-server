package org.idp.server;

import java.util.logging.Logger;
import org.idp.server.core.OAuthRequestParameters;
import org.idp.server.core.TokenIssuer;
import org.idp.server.core.oauth.OAuthBadRequestException;
import org.idp.server.core.oauth.validator.OAuthRequestInitialValidator;
import org.idp.server.io.OAuthRequest;
import org.idp.server.io.OAuthRequestResponse;

/** OAuthApi */
public class OAuthApi {

  OAuthRequestInitialValidator initialValidator = new OAuthRequestInitialValidator();
  Logger log = Logger.getLogger(OAuthApi.class.getName());

  public OAuthRequestResponse request(OAuthRequest oAuthRequest) {
    OAuthRequestParameters oAuthRequestParameters = oAuthRequest.toParameters();
    TokenIssuer tokenIssuer = oAuthRequest.toTokenIssuer();
    try {
      initialValidator.validate(oAuthRequestParameters);
      return new OAuthRequestResponse();
    } catch (OAuthBadRequestException exception) {
      log.warning(exception.getMessage());
      return new OAuthRequestResponse();
    } catch (Exception exception) {
      return new OAuthRequestResponse();
    }
  }
}
