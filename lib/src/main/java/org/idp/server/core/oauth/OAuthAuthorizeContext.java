package org.idp.server.core.oauth;

import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.ExpiresDateTime;
import org.idp.server.core.type.ResponseType;
import org.idp.server.core.type.TokenIssuer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;

/**
 * OAuthAuthorizeContext
 */
public class OAuthAuthorizeContext {
    AuthorizationRequest authorizationRequest;
    ServerConfiguration serverConfiguration;
    ClientConfiguration clientConfiguration;

    public OAuthAuthorizeContext() {}

    public OAuthAuthorizeContext(AuthorizationRequest authorizationRequest, ServerConfiguration serverConfiguration, ClientConfiguration clientConfiguration) {
        this.authorizationRequest = authorizationRequest;
        this.serverConfiguration = serverConfiguration;
        this.clientConfiguration = clientConfiguration;
    }

    public AuthorizationRequest authorizationRequest() {
        return authorizationRequest;
    }

    public ServerConfiguration serverConfiguration() {
        return serverConfiguration;
    }

    public ClientConfiguration clientConfiguration() {
        return clientConfiguration;
    }

    public TokenIssuer tokenIssuer() {
        return serverConfiguration.issuer();
    }

    public ResponseType responseType() {
        return authorizationRequest.responseType();
    }

    public ExpiresDateTime authorizationCodeGrantExpiresDateTime() {
        LocalDateTime localDateTime = UtcDateTime.now();
        int duration = serverConfiguration.authorizationCodeValidDuration();
        return new ExpiresDateTime(localDateTime.plusMinutes(duration));
    }

}
