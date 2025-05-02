package org.idp.server.core.verifiable_credential.handler.io;

import java.util.Map;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.verifiable_credential.request.BatchCredentialRequestParameters;

public class BatchCredentialRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, Object> params;
  String clientCert;

  public BatchCredentialRequest(
      Tenant tenant, String authorizationHeaders, Map<String, Object> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public BatchCredentialRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public Tenant tenant() {
    return tenant;
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

  public BatchCredentialRequestParameters toParameters() {
    return new BatchCredentialRequestParameters(params);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
