package org.idp.server.ciba.grant;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.ciba.CibaRequestContext;
import org.idp.server.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.ciba.response.BackchannelAuthenticationResponse;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.AuthReqId;
import org.idp.server.type.ciba.Interval;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ExpiredAt;
import org.idp.server.type.oauth.Scopes;

public class CIbaGrantFactory {

  public CibaGrant create(
      CibaRequestContext context, BackchannelAuthenticationResponse response, User user) {
    BackchannelAuthenticationRequestIdentifier identifier =
        context.backchannelAuthenticationRequestIdentifier();
    ClientId clientId = context.clientId();
    Scopes scopes = context.scopes();
    AuthorizationGrant authorizationGrant =
        new AuthorizationGrant(user, clientId, scopes, new ClaimsPayload(), new CustomProperties());
    AuthReqId authReqId = response.authReqId();
    LocalDateTime now = SystemDateTime.now();
    ExpiredAt expiredAt = new ExpiredAt(now.plusSeconds(context.expiresIn().value()));
    Interval interval = context.interval();
    return new CibaGrant(identifier, authorizationGrant, authReqId, expiredAt, interval);
  }
}
