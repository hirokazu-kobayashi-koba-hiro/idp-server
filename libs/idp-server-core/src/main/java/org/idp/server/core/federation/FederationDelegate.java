package org.idp.server.core.federation;

import org.idp.server.core.oauth.identity.User;

public interface FederationDelegate {

  User find(String tokenIssuer, String providerId, String providerUserId);
}
