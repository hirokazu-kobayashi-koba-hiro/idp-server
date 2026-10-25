/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.idp.server.core.openid.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;

/**
 * An authorization request that only signs the user in again for an account link, and then hands
 * the browser back to that link instead of issuing anything.
 *
 * <p>{@code /linking/start} needs a recent sign-in in the browser. When there is none it sends the
 * browser through the ordinary authorization flow — the same view, the same authentication policy,
 * the same place the OP session is written — with the link's state in {@link #STATE_PARAMETER}.
 * Such a request ends at {@link #resumeUri} with no code: the sign-in is what was wanted, and the
 * client never asked for a grant.
 *
 * <p>The parameter is an ordinary custom parameter anyone can put on a request. That is harmless:
 * the resume URI is fixed to this server's {@code /linking/start}, which checks the browser's
 * session and the link afresh. A forged state only gets its sender to a page they could open
 * directly.
 */
public final class AccountLinkingReauthentication {

  /** The custom parameter carrying the link's state. */
  public static final String STATE_PARAMETER = "idp_linking_state";

  /**
   * Added to the resume URI: the browser is on its way back from the sign-in {@code /linking/start}
   * sent it to, whether it signed in or cancelled. It says nothing about authentication, only that
   * the round trip happened. {@code /linking/start} reached with it and still without a recent
   * sign-in does not send the browser round again, which would loop where the session cannot be
   * written.
   */
  public static final String RETURNED_FROM_SIGN_IN_PARAMETER = "returned_from_sign_in";

  private AccountLinkingReauthentication() {}

  public static boolean isRequestedBy(AuthorizationRequest authorizationRequest) {
    return stateOf(authorizationRequest) != null;
  }

  /** The link's state, or {@code null} when the request is not one of these. */
  public static String stateOf(AuthorizationRequest authorizationRequest) {
    if (authorizationRequest == null || authorizationRequest.customParams() == null) {
      return null;
    }
    String state = authorizationRequest.customParams().values().get(STATE_PARAMETER);
    return state == null || state.isEmpty() ? null : state;
  }

  /**
   * Where the browser goes once signed in, or once it gives up.
   *
   * @param issuer the tenant's authorization server issuer, which already carries the tenant
   */
  public static String resumeUri(String issuer, String state) {
    return String.format(
        "%s/v1/linking/start?state=%s&%s=true",
        issuer, URLEncoder.encode(state, StandardCharsets.UTF_8), RETURNED_FROM_SIGN_IN_PARAMETER);
  }
}
