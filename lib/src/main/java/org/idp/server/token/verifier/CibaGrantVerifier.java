package org.idp.server.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;

public class CibaGrantVerifier {

  public CibaGrantVerifier() {}

  public void verify(
      TokenRequestContext context, BackchannelAuthenticationRequest request, CibaGrant cibaGrant) {
    throwIfExpired(cibaGrant);
    throwIfAuthorizedPending(cibaGrant);
  }

  void throwIfExpired(CibaGrant cibaGrant) {
    LocalDateTime now = SystemDateTime.now();
    if (cibaGrant.isExpire(now)) {
      throw new TokenBadRequestException(
          "expired_token",
          "The auth_req_id has expired. The Client will need to make a new Authentication Request.");
    }
  }

  void throwIfAuthorizedPending(CibaGrant cibaGrant) {
    if (cibaGrant.isAuthorizationPending()) {
      throw new TokenBadRequestException(
          "authorization_pending",
          "The authorization request is still pending as the end-user hasn't yet been authenticated.");
    }
  }
}
