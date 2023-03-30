package org.idp.server.io;

import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.type.status.OAuthAuthorizeStatus;

/**
 * OAuthAuthorizeResponse
 */
public class OAuthAuthorizeResponse {
    OAuthAuthorizeStatus status;
    AuthorizationResponse authorizationResponse;

    public OAuthAuthorizeResponse() {}

    public OAuthAuthorizeResponse(OAuthAuthorizeStatus status, AuthorizationResponse authorizationResponse) {
        this.status = status;
        this.authorizationResponse = authorizationResponse;
    }

    public OAuthAuthorizeStatus status() {
        return status;
    }

    public AuthorizationResponse authorizationResponse() {
        return authorizationResponse;
    }
}
