package org.idp.server.core.federation.io;

import java.util.Map;
import org.idp.server.core.federation.FederationCallbackParameters;
import org.idp.server.core.federation.sso.SsoProvider;
import org.idp.server.core.federation.sso.SsoState;
import org.idp.server.core.federation.sso.SsoStateCoder;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class FederationCallbackRequest {

  Map<String, String[]> params;

  public FederationCallbackRequest() {}

  public FederationCallbackRequest(Map<String, String[]> params) {
    this.params = params;
  }

  public FederationCallbackParameters parameters() {
    return new FederationCallbackParameters(params);
  }

  public String state() {
    if (parameters().hasState()) {
      return parameters().state().value();
    }
    return "";
  }

  public SsoState ssoState() {
    if (parameters().hasState()) {
      return SsoStateCoder.decode(state());
    }
    return new SsoState();
  }

  public TenantIdentifier tenantIdentifier() {
    return ssoState().tenantIdentifier();
  }

  public SsoProvider ssoProvider() {
    if (parameters().hasState()) {
      return ssoState().ssoProvider();
    }
    return new SsoProvider();
  }
}
