package org.idp.server.type.oidc;

import java.util.Objects;

/**
 * prompt
 *
 * <p>OPTIONAL. Space delimited, case sensitive list of ASCII string values that specifies whether
 * the Authorization Server prompts the End-User for reauthentication and consent. The defined
 * values are:
 *
 * <p>none The Authorization Server MUST NOT display any authentication or consent user interface
 * pages. An error is returned if an End-User is not already authenticated or the Client does not
 * have pre-configured consent for the requested Claims or does not fulfill other conditions for
 * processing the request. The error code will typically be login_required, interaction_required, or
 * another code defined in Section 3.1.2.6. This can be used as a method to check for existing
 * authentication and/or consent.
 *
 * <p>login The Authorization Server SHOULD prompt the End-User for reauthentication. If it cannot
 * reauthenticate the End-User, it MUST return an error, typically login_required.
 *
 * <p>consent The Authorization Server SHOULD prompt the End-User for consent before returning
 * information to the Client. If it cannot obtain consent, it MUST return an error, typically
 * consent_required.
 *
 * <p>select_account The Authorization Server SHOULD prompt the End-User to select a user account.
 * This enables an End-User who has multiple accounts at the Authorization Server to select amongst
 * the multiple accounts that they might have current sessions for. If it cannot obtain an account
 * selection choice made by the End-User, it MUST return an error, typically
 * account_selection_required.
 *
 * <p>The prompt parameter can be used by the Client to make sure that the End-User is still present
 * for the current session or to bring attention to the request. If this parameter contains none
 * with any other value, an error is returned.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public enum Prompt {
  none,
  login,
  consent,
  select_account,
  undefined,
  unknown;

  public static Prompt of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (Prompt prompt : Prompt.values()) {
      if (prompt.name().equals(value)) {
        return prompt;
      }
    }
    return unknown;
  }

  public boolean isUnknown() {
    return this == unknown;
  }

  public boolean isNone() {
    return this == none;
  }
}
