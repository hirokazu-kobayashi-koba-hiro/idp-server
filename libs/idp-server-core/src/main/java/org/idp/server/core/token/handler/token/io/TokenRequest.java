package org.idp.server.core.token.handler.token.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.http.BasicAuth;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.token.TokenRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;
  Map<String, Object> customProperties = new HashMap<>();

  public TokenRequest(Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public TokenRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getClientCert() {
    return clientCert;
  }

  public Map<String, Object> customProperties() {
    return customProperties;
  }

  public TokenRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public RequestedClientId clientId() {
    TokenRequestParameters parameters = toParameters();
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }
    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public Tenant tenant() {
    return tenant;
  }

  public TokenRequestParameters toParameters() {
    return new TokenRequestParameters(params);
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public boolean isRefreshTokenGrant() {
    TokenRequestParameters parameters = toParameters();
    return parameters.isRefreshTokenGrant();
  }
}
