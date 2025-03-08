package org.idp.server.core.federation;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface FederationDelegate {

  User find(TokenIssuer tokenIssuer, String username);
}
