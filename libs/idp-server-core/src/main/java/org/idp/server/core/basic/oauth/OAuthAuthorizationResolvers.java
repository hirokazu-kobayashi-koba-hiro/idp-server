package org.idp.server.core.basic.oauth;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.type.exception.UnSupportedException;

public class OAuthAuthorizationResolvers {

  Map<OAuthAuthorizationType, OAuthAuthorizationResolver> resolvers;

  public OAuthAuthorizationResolvers() {
    this.resolvers = new HashMap<>();
    resolvers.put(
        OAuthAuthorizationType.CLIENT_CREDENTIALS, new ClientCredentialsAuthorizationResolver());
    resolvers.put(
        OAuthAuthorizationType.RESOURCE_OWNER_PASSWORD_CREDENTIALS,
        new ResourceOwnerPasswordCredentialsAuthorizationResolver());
  }

  public OAuthAuthorizationResolver get(OAuthAuthorizationType type) {
    OAuthAuthorizationResolver resolver = resolvers.get(type);

    if (resolver == null) {
      throw new UnSupportedException("Unsupported OAuth authorization type: " + type.name());
    }

    return resolver;
  }
}
