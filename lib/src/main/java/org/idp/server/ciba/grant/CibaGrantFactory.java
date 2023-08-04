package org.idp.server.ciba.grant;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.ciba.CibaRequestContext;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.grant.AuthorizationGrantBuilder;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.ciba.Interval;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.Scopes;

public class CibaGrantFactory {

  CibaRequestContext context;
  BackchannelAuthenticationResponse response;
  User user;
  Authentication authentication;

  public CibaGrantFactory(
      CibaRequestContext context,
      BackchannelAuthenticationResponse response,
      User user,
      Authentication authentication) {
    this.context = context;
    this.response = response;
    this.user = user;
    this.authentication = authentication;
  }

  public CibaGrant create() {
    BackchannelAuthenticationRequestIdentifier identifier =
        context.backchannelAuthenticationRequestIdentifier();
    ClientId clientId = context.clientId();
    Scopes scopes = context.scopes();
    // TODO authorization_details
    AuthorizationGrantBuilder builder =
        new AuthorizationGrantBuilder(clientId, scopes).add(user).add(authentication);
    if (user.hasCustomProperties()) {
      builder.add(user.customProperties());
    }
    AuthorizationGrant authorizationGrant = builder.build();
    AuthReqId authReqId = response.authReqId();
    LocalDateTime now = SystemDateTime.now();
    ExpiredAt expiredAt = new ExpiredAt(now.plusSeconds(context.expiresIn().value()));
    Interval interval = context.interval();
    return new CibaGrant(
        identifier,
        authorizationGrant,
        authReqId,
        expiredAt,
        interval,
        CibaGrantStatus.authorization_pending);
  }
}
