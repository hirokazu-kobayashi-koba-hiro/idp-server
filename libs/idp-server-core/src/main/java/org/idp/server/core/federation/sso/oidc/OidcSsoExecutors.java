package org.idp.server.core.federation.sso.oidc;

import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.core.federation.sso.SsoProvider;

public class OidcSsoExecutors {

  Map<SsoProvider, OidcSsoExecutor> executors;

  public OidcSsoExecutors(Map<SsoProvider, OidcSsoExecutor> executors) {
    this.executors = executors;
  }

  public OidcSsoExecutor get(SsoProvider provider) {
    OidcSsoExecutor oidcSsoExecutor = executors.get(provider);

    if (oidcSsoExecutor == null) {
      throw new UnSupportedException("No OidcSsoExecutor found for provider " + provider.name());
    }

    return oidcSsoExecutor;
  }
}
