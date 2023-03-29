package org.idp.server.io;

import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.TokenIssuer;

import java.util.Map;

/**
 * OAuthAuthorizeRequest
 */
public class OAuthAuthorizeRequest {
    String id;
    Map<String, Object> customProperties;
    //TODO user

    public OAuthAuthorizeRequest(String id) {
        this.id = id;
    }

    public OAuthAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
        this.customProperties = customProperties;
        return this;
    }

    public AuthorizationRequestIdentifier toIdentifier() {
        return new AuthorizationRequestIdentifier(id);
    }

    public Map<String, Object> customProperties() {
        return customProperties;
    }

    public TokenIssuer toTokenIssuer() {
        return null;
    }
}
