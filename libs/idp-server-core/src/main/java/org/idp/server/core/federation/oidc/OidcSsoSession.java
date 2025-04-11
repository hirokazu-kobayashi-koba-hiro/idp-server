package org.idp.server.core.federation.oidc;

import java.io.Serializable;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.federation.SsoSessionIdentifier;
import org.idp.server.core.tenant.TenantIdentifier;

public class OidcSsoSession implements Serializable, JsonReadable {

  String authorizationRequestId;
  String tenantId;
  String tokenIssuer;
  String state;
  String nonce;
  String idpId;
  String clientId;
  String redirectUri;
  String authorizationRequestUri;

  public OidcSsoSession() {}

  public OidcSsoSession(
      String authorizationRequestId,
      String tenantId,
      String tokenIssuer,
      String state,
      String nonce,
      String idpId,
      String clientId,
      String redirectUri,
      String authorizationRequestUri) {
    this.authorizationRequestId = authorizationRequestId;
    this.tenantId = tenantId;
    this.tokenIssuer = tokenIssuer;
    this.state = state;
    this.nonce = nonce;
    this.idpId = idpId;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.authorizationRequestUri = authorizationRequestUri;
  }

  public String authorizationRequestId() {
    return authorizationRequestId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String tokenIssuer() {
    return tokenIssuer;
  }

  public String state() {
    return state;
  }

  public String nonce() {
    return nonce;
  }

  public String idpId() {
    return idpId;
  }

  public String clientId() {
    return clientId;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public String authorizationRequestUri() {
    return authorizationRequestUri;
  }

  public boolean exists() {
    return Objects.nonNull(state) && !state.isEmpty();
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }

  public SsoSessionIdentifier ssoSessionIdentifier() {
    return new SsoSessionIdentifier(state);
  }
}
