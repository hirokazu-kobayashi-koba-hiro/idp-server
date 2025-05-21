package org.idp.server.core.verifiable_credential.handler.io;

import java.util.Map;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.verifiable_credential.request.CredentialRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CredentialRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, Object> params;
  String clientCert;

  public CredentialRequest(Tenant tenant, String authorizationHeaders, Map<String, Object> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public CredentialRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public String getClientCert() {
    return clientCert;
  }

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(authorizationHeaders);
  }

  public CredentialRequestParameters toParameters() {
    return new CredentialRequestParameters(params);
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
