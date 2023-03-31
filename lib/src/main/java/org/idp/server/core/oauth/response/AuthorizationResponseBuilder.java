package org.idp.server.core.oauth.response;

import org.idp.server.basic.http.QueryParams;
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
    QueryParams queryParams;
    
    public AuthorizationResponseBuilder(RedirectUri redirectUri, ResponseModeValue responseModeValue, TokenIssuer tokenIssuer) {
        this.redirectUri = redirectUri;
        this.responseModeValue = responseModeValue;
        this.tokenIssuer = tokenIssuer;
        this.queryParams = new QueryParams();
        queryParams.add("iss", tokenIssuer.value());
    }


    public AuthorizationResponseBuilder add(AuthorizationCode authorizationCode) {
        this.authorizationCode = authorizationCode;
        this.queryParams.add("code", authorizationCode.value());
        return this;
    }

    public AuthorizationResponseBuilder add(State state) {
        this.state = state;
        this.queryParams.add("state", state.value());
        return this;
    }

    public AuthorizationResponseBuilder add(Error error) {
        this.error = error;
        this.queryParams.add("error", error.value());
        return this;
    }

    public AuthorizationResponseBuilder add(ErrorDescription errorDescription) {
        this.errorDescription = errorDescription;
        this.queryParams.add("error_description", errorDescription.value());
        return this;
    }

    public AuthorizationResponse build() {
        return new AuthorizationResponse(redirectUri, responseModeValue, authorizationCode, state, tokenIssuer, error, errorDescription, queryParams);
    }
}
