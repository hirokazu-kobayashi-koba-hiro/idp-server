package org.idp.server.core.verifiable_credential.handler.io;

import java.util.Map;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.verifiablecredential.TransactionId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.verifiable_credential.request.DeferredCredentialRequestParameters;

public class DeferredCredentialRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, Object> params;
  String clientCert;

  public DeferredCredentialRequest(
      Tenant tenant, String authorizationHeaders, Map<String, Object> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public DeferredCredentialRequest setClientCert(String clientCert) {
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

  public DeferredCredentialRequestParameters toParameters() {
    return new DeferredCredentialRequestParameters(params);
  }

  public TransactionId transactionId() {
    return toParameters().transactionId();
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
