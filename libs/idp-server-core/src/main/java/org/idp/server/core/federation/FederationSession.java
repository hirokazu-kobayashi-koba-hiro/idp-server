package org.idp.server.core.federation;

import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;

public class FederationSession implements JsonReadable {

  String authorizationRequestId;
  String tenantId;
  String tokenIssuer;
  String state;
  String nonce;
  String idpId;
  String clientId;
  String redirectUri;
  String authorizationRequestUri;

  public FederationSession() {}

  public FederationSession(
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
}
