package org.idp.server.core.oauth.response;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.type.AuthorizationCode;

public class AuthorizationCodeResponseCreator implements AuthorizationResponseCreator, AuthorizationCodeCreatable {


    @Override
    public AuthorizationResponseParameter create(OAuthRequestContext oAuthRequestContext) {
        AuthorizationCode authorizationCode = createAuthorizationCode();
        return null;
    }
}
