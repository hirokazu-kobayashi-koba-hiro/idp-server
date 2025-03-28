package org.idp.server.core.ciba.grant;

import java.time.LocalDateTime;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oauth.client.ClientIdentifier;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.ciba.AuthReqId;
import org.idp.server.core.type.ciba.Interval;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.type.oauth.Scopes;

public class CibaGrant {

  BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier =
      new BackchannelAuthenticationRequestIdentifier();
  AuthorizationGrant authorizationGrant;
  AuthReqId authReqId;
  ExpiredAt expiredAt;
  Interval interval;
  CibaGrantStatus status;

  public CibaGrant() {}

  public CibaGrant(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier,
      AuthorizationGrant authorizationGrant,
      AuthReqId authReqId,
      ExpiredAt expiredAt,
      Interval interval,
      CibaGrantStatus status) {
    this.backchannelAuthenticationRequestIdentifier = backchannelAuthenticationRequestIdentifier;
    this.authorizationGrant = authorizationGrant;
    this.authReqId = authReqId;
    this.expiredAt = expiredAt;
    this.interval = interval;
    this.status = status;
  }

  public BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier() {
    return backchannelAuthenticationRequestIdentifier;
  }

  public TenantIdentifier tenantIdentifier() {
    return authorizationGrant.tenantIdentifier();
  }

  public User user() {
    return authorizationGrant.user();
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public ClientIdentifier clientIdentifier() {
    return authorizationGrant.clientIdentifier();
  }

  public AuthReqId authReqId() {
    return authReqId;
  }

  public boolean isGrantedClient(ClientIdentifier clientIdentifier) {
    return authorizationGrant.isGranted(clientIdentifier);
  }

  public boolean isExpire(LocalDateTime other) {
    return expiredAt.isExpire(other);
  }

  public boolean exists() {
    return backchannelAuthenticationRequestIdentifier.exists();
  }

  public Scopes scopes() {
    return authorizationGrant.scopes();
  }

  public ExpiredAt expiredAt() {
    return expiredAt;
  }

  public Interval interval() {
    return interval;
  }

  public CibaGrantStatus status() {
    return status;
  }

  public boolean isAuthorizationPending() {
    return status.isAuthorizationPending();
  }

  public boolean isAuthorized() {
    return status.isAuthorized();
  }

  public boolean isAccessDenied() {
    return status.isAccessDenied();
  }

  public RequestedClientId requestedClientId() {
    return authorizationGrant.requestedClientId();
  }

  public CibaGrant update(CibaGrantStatus cibaGrantStatus) {
    return new CibaGrant(
        backchannelAuthenticationRequestIdentifier,
        authorizationGrant,
        authReqId,
        expiredAt,
        interval,
        cibaGrantStatus);
  }
}
