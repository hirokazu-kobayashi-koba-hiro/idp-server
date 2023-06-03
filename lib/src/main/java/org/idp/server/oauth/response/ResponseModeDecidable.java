package org.idp.server.oauth.response;

import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.ResponseType;
import org.idp.server.type.oidc.ResponseMode;

public interface ResponseModeDecidable {

  default ResponseModeValue decideResponseModeValue(
      ResponseType responseType, ResponseMode responseMode) {
    if (responseMode.isDefinedResponseModeValue()) {
      return new ResponseModeValue(responseMode.responseModeValue());
    } else if (responseType.isAuthorizationCodeFlow()
        || responseType.isUndefined()
        || responseType.isUnknown()) {
      return new ResponseModeValue("?");
    } else {
      return new ResponseModeValue("#");
    }
  }
}
