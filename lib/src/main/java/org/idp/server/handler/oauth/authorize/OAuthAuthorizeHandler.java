package org.idp.server.handler.oauth.authorize;

import org.idp.server.core.oauth.OAuthAuthorizeContext;
import org.idp.server.core.oauth.response.AuthorizationCodeResponseCreator;
import org.idp.server.core.oauth.response.AuthorizationResponse;
import org.idp.server.core.oauth.response.AuthorizationResponseCreator;
import org.idp.server.core.type.ResponseType;
import org.idp.server.core.type.status.OAuthAuthorizeStatus;
import org.idp.server.io.OAuthAuthorizeResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OAuthAuthorizeHandler
 */
public class OAuthAuthorizeHandler {

    static Map<ResponseType, AuthorizationResponseCreator> map = new HashMap<>();

    static {
        map.put(ResponseType.code, new AuthorizationCodeResponseCreator());
    }

    public AuthorizationResponse handle(OAuthAuthorizeContext context) {
        AuthorizationResponseCreator authorizationResponseCreator = map.get(context.responseType());
        if (Objects.isNull(authorizationResponseCreator)) {
            throw new RuntimeException("not support request type");
        }
       return authorizationResponseCreator.create(context);
    }
}
