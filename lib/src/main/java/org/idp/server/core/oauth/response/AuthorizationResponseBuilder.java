package org.idp.server.core.oauth.response;

import org.idp.server.core.type.*;
import org.idp.server.core.type.Error;

public class AuthorizationResponseBuilder {
    RedirectUri redirectUri;
    ResponseModeValue responseModeValue;
    AuthorizationCode authorizationCode;
    State state;
    TokenIssuer tokenIssuer;
    Error error;
    ErrorDescription errorDescription;
    
    public AuthorizationResponseBuilder() {}

    public AuthorizationResponseBuilder add(RedirectUri redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }

    public AuthorizationResponseBuilder add(ResponseModeValue responseModeValue) {
        this.responseModeValue = responseModeValue;
        return this;
    }

    public AuthorizationResponseBuilder add(AuthorizationCode authorizationCode) {
        this.authorizationCode = authorizationCode;
        return this;
    }

    public AuthorizationResponseBuilder setState(State state) {
        this.state = state;
        return this;
    }

    public AuthorizationResponseBuilder add(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
        return this;
    }

    public AuthorizationResponseBuilder add(Error error) {
        this.error = error;
        return this;
    }

    public AuthorizationResponseBuilder add(ErrorDescription errorDescription) {
        this.errorDescription = errorDescription;
        return this;
    }

    public AuthorizationResponse build() {
        return new AuthorizationResponse(redirectUri, responseModeValue, authorizationCode, state, tokenIssuer, error, errorDescription);
    }
}
