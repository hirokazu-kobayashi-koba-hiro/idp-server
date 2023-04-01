package org.idp.server.core.oauth.grant;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.type.AuthorizationCode;
import org.idp.server.core.type.ExpiresDateTime;

public class AuthorizationCodeGrantCreator {

    public static AuthorizationCodeGrant create(OAuthAuthorizeContext oAuthAuthorizeContext, AuthorizationResponse authorizationResponse) {
        AuthorizationRequestIdentifier authorizationRequestIdentifier = oAuthAuthorizeContext.authorizationRequest().identifier();
        AuthorizationCode authorizationCode = authorizationResponse.authorizationCode();
        ExpiresDateTime expiresDateTime = oAuthAuthorizeContext.authorizationCodeGrantExpiresDateTime();
        return new AuthorizationCodeGrant(authorizationRequestIdentifier, authorizationCode, expiresDateTime);
    }
}
