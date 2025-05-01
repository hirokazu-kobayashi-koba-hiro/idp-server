package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.basic.type.extension.ResponseModeValue;
import org.idp.server.basic.type.oauth.ResponseType;
import org.idp.server.basic.type.oidc.ResponseMode;

public interface ResponseModeDecidable {

  default ResponseModeValue decideResponseModeValue(
      ResponseType responseType, ResponseMode responseMode) {

    if (responseMode.isDefinedResponseModeValue()) {

      return new ResponseModeValue(responseMode.responseModeValue());
    } else if (responseType.isAuthorizationCodeFlow()
        || responseType.isUndefined()
        || responseType.isUnknown()) {

      return ResponseModeValue.query();
    } else {

      return ResponseModeValue.fragment();
    }
  }

  default boolean isJwtMode(
      AuthorizationProfile profile, ResponseType responseType, ResponseMode responseMode) {
    if (responseMode.isJwtMode()) {
      return true;
    }
    if (profile.isFapiAdvance() && !responseType.isCodeIdToken()) {
      return true;
    }
    return false;
  }
}
