package org.idp.server.core.oauth.interaction;

import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.sharedsignal.DefaultEventType;

import java.util.Map;
import java.util.Objects;

public class OAuthUserInteractionResult {

    OAuthUserInteractionType type;
    User user;
    Authentication authentication;
    Map<String, Object> response;
    DefaultEventType eventType;

    public OAuthUserInteractionResult(OAuthUserInteractionType type, Map<String, Object> response, DefaultEventType eventType) {
        this.type = type;
        this.response = response;
        this.eventType = eventType;
    }

    public OAuthUserInteractionResult(OAuthUserInteractionType type, User user, Authentication authentication, Map<String, Object> response, DefaultEventType eventType) {
        this.type = type;
        this.user = user;
        this.authentication = authentication;
        this.response = response;
        this.eventType = eventType;
    }

    public OAuthUserInteractionType type() {
        return type;
    }

    public User user() {
        return user;
    }

    public Authentication authentication() {
        return authentication;
    }

    public Map<String, Object> response() {
        return response;
    }

    public DefaultEventType eventType() {
        return eventType;
    }

    public boolean hasUser() {
        return Objects.nonNull(user) && user.exists();
    }

    public boolean hasAuthentication() {
        return Objects.nonNull(authentication) && authentication.exists();
    }
}
