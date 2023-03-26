package org.idp.server.io;

import org.idp.server.core.oauth.OAuthRequestIdentifier;

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

    public OAuthRequestIdentifier toIdentifier() {
        return new OAuthRequestIdentifier(id);
    }

    public Map<String, Object> customProperties() {
        return customProperties;
    }
}
