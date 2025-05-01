package org.idp.server.core.federation.oidc;

import java.util.UUID;
import org.idp.server.basic.http.QueryParams;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.SsoProvider;
import org.idp.server.core.federation.SsoState;
import org.idp.server.core.federation.SsoStateCoder;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;

public class OidcSsoSessionCreator {

  OidcSsoConfiguration configuration;
  Tenant tenant;
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  FederationType federationType;
  SsoProvider ssoProvider;

  public OidcSsoSessionCreator(
      OidcSsoConfiguration oidcSsoConfiguration,
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider) {
    this.configuration = oidcSsoConfiguration;
    this.tenant = tenant;
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.federationType = federationType;
    this.ssoProvider = ssoProvider;
  }

  public OidcSsoSession create() {
    String authorizationEndpoint = configuration.authorizationEndpoint();
    String sessionId = UUID.randomUUID().toString();
    String tenantId = tenant.identifierValue();
    SsoState ssoState = new SsoState(sessionId, tenantId);
    String state = SsoStateCoder.encode(ssoState);
    String nonce = UUID.randomUUID().toString();

    QueryParams queryParams = new QueryParams();
    queryParams.add("client_id", configuration.clientId());
    queryParams.add("redirect_uri", configuration.redirectUri());
    queryParams.add("response_type", "code");
    queryParams.add("state", state);
    queryParams.add("nonce", nonce);
    queryParams.add("scope", configuration.scopeAsString());

    String authorizationRequestUri =
        String.format("%s?%s", authorizationEndpoint, queryParams.params());

    return new OidcSsoSession(
        sessionId,
        authorizationRequestIdentifier.value(),
        tenant.identifierValue(),
        tenant.tokenIssuerValue(),
        state,
        nonce,
        configuration.type(),
        configuration.clientId(),
        configuration.redirectUri(),
        authorizationRequestUri);
  }
}
