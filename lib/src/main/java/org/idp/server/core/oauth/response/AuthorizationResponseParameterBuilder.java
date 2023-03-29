package org.idp.server.core.oauth.response;

import org.idp.server.core.type.*;
import org.idp.server.core.type.Error;

public class AuthorizationResponseParameterBuilder {
    RedirectUri redirectUri;
    ResponseModeValue responseModeValue;
    AuthorizationCode authorizationCode;
    State state;
    TokenIssuer tokenIssuer;
    Error error;
    ErrorDescription errorDescription;
    
    public AuthorizationResponseParameterBuilder() {}

    public AuthorizationResponseParameterBuilder add(RedirectUri redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }

    public AuthorizationResponseParameterBuilder add(ResponseModeValue responseModeValue) {
        this.responseModeValue = responseModeValue;
        return this;
    }

    public AuthorizationResponseParameterBuilder add(AuthorizationCode authorizationCode) {
        this.authorizationCode = authorizationCode;
        return this;
    }

    public AuthorizationResponseParameterBuilder add(State state) {
        this.state = state;
        return this;
    }

    public AuthorizationResponseParameterBuilder add(TokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
        return this;
    }

    public AuthorizationResponseParameterBuilder add(Error error) {
        this.error = error;
        return this;
    }

    public AuthorizationResponseParameterBuilder add(ErrorDescription errorDescription) {
        this.errorDescription = errorDescription;
        return this;
    }

    public AuthorizationResponseParameter build() {
        return new AuthorizationResponseParameter(redirectUri, responseModeValue, authorizationCode, state, tokenIssuer, error, errorDescription);
    }
}
