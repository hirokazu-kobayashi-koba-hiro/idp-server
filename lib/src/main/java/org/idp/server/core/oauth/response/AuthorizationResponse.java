package org.idp.server.core.oauth.response;

import org.idp.server.core.type.*;
import org.idp.server.core.type.Error;

public class AuthorizationResponse {
    RedirectUri redirectUri;
    ResponseModeValue responseModeValue;
    AuthorizationCode authorizationCode;
    State state;
    TokenIssuer tokenIssuer;
    Error error;
    ErrorDescription errorDescription;

    AuthorizationResponse(RedirectUri redirectUri, ResponseModeValue responseModeValue, AuthorizationCode authorizationCode, State state, TokenIssuer tokenIssuer, Error error, ErrorDescription errorDescription) {
        this.redirectUri = redirectUri;
        this.responseModeValue = responseModeValue;
        this.authorizationCode = authorizationCode;
        this.state = state;
        this.tokenIssuer = tokenIssuer;
        this.error = error;
        this.errorDescription = errorDescription;
    }


}
