package org.idp.server.type.oidc;

import java.util.Objects;

/**
 * display OPTIONAL.
 *
 * <p>ASCII string value that specifies how the Authorization Server displays the authentication and
 * consent user interface pages to the End-User. The defined values are:
 *
 * <p>page The Authorization Server SHOULD display the authentication and consent UI consistent with
 * a full User Agent page view. If the display parameter is not specified, this is the default
 * display mode.
 *
 * <p>popup The Authorization Server SHOULD display the authentication and consent UI consistent
 * with a popup User Agent window. The popup User Agent window should be of an appropriate size for
 * a login-focused dialog and should not obscure the entire window that it is popping up over.
 *
 * <p>touch The Authorization Server SHOULD display the authentication and consent UI consistent
 * with a device that leverages a touch interface.
 *
 * <p>wap The Authorization Server SHOULD display the authentication and consent UI consistent with
 * a "feature phone" type display.
 *
 * <p>The Authorization Server MAY also attempt to detect the capabilities of the User Agent and
 * present an appropriate display.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 */
public enum Display {
  page,
  popup,
  touch,
  wap,
  undefined,
  unknown;

  public static Display of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (Display display : Display.values()) {
      if (display.name().equals(value)) {
        return display;
      }
    }
    return unknown;
  }

  public boolean isUnknown() {
    return this == unknown;
  }

  public boolean isDefined() {
    return this != undefined;
  }
}
