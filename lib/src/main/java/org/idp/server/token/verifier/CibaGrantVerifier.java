package org.idp.server.token.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.ciba.grant.CibaGrant;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;

/**
 * CibaGrantVerifier
 *
 * <p>If the Token Request is invalid or unauthorized, the OpenID Provider constructs an error
 * response according to Section 3.1.3.4 Token Error Response of [OpenID.Core]. In addition to the
 * error codes defined in Section 5.2 of [RFC6749], the following error codes defined in the OAuth
 * Device Flow are also applicable:
 *
 * @see <a
 *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.11">Token
 *     Error Response</a>
 */
public class CibaGrantVerifier {

  public CibaGrantVerifier() {}

  public void verify(
      TokenRequestContext context, BackchannelAuthenticationRequest request, CibaGrant cibaGrant) {
    throwIfInvalidAuthReqId(context, cibaGrant);
    throwIfExpired(cibaGrant);
    throwIfAuthorizedPending(cibaGrant);
    throwIfAccessDenied(cibaGrant);
  }

  /**
   * invalid_grant
   *
   * <p>If the auth_req_id is invalid or was issued to another Client, an invalid_grant error MUST be returned as described in Section 5.2 of [RFC6749].
   *
   * @param context
   * @param cibaGrant
   */
  void throwIfInvalidAuthReqId(TokenRequestContext context, CibaGrant cibaGrant) {
    if (!cibaGrant.exists()) {
      throw new TokenBadRequestException("invalid_grant",
              String.format("auth_req_id is invalid (%s)", context.authReqId().value()));
    }
    if (!cibaGrant.isGrantedClient(context.clientId())) {
      throw new TokenBadRequestException("invalid_grant",
              String.format("auth_req_id is invalid (%s)", context.authReqId().value()));
    }
  }

  /**
   * expired_token
   *
   * <p>The auth_req_id has expired. The Client will need to make a new Authentication Request.
   *
   * @param cibaGrant
   */
  void throwIfExpired(CibaGrant cibaGrant) {
    LocalDateTime now = SystemDateTime.now();
    if (cibaGrant.isExpire(now)) {
      throw new TokenBadRequestException(
          "expired_token",
          "The auth_req_id has expired. The Client will need to make a new Authentication Request.");
    }
  }

  /**
   * authorization_pending
   *
   * <p>The authorization request is still pending as the end-user hasn't yet been authenticated.
   *
   * @param cibaGrant
   */
  void throwIfAuthorizedPending(CibaGrant cibaGrant) {
    if (cibaGrant.isAuthorizationPending()) {
      throw new TokenBadRequestException(
          "authorization_pending",
          "The authorization request is still pending as the end-user hasn't yet been authenticated.");
    }
  }

  /**
   * access_denied
   *
   * <p>The end-user denied the authorization request.
   *
   * @param cibaGrant
   */
  void throwIfAccessDenied(CibaGrant cibaGrant) {
    if (cibaGrant.isAccessDenied()) {
      throw new TokenBadRequestException(
          "access_denied", "The end-user denied the authorization request.");
    }
  }
}
