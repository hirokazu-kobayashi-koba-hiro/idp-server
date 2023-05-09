package org.idp.server.ciba.grant;

import java.time.LocalDateTime;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.ciba.Interval;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;

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

  public User user() {
    return authorizationGrant.user();
  }

  public AuthorizationGrant authorizationGrant() {
    return authorizationGrant;
  }

  public AuthReqId authReqId() {
    return authReqId;
  }

  public boolean isGrantedClient(ClientId clientId) {
    return authorizationGrant.isGranted(clientId);
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
