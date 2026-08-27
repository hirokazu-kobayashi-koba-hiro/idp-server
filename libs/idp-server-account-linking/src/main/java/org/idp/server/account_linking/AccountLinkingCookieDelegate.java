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

package org.idp.server.account_linking;

import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Carries the linking browser binding secret between {@code /linking/start} and the callback.
 *
 * <p>Separate from the OIDC session cookies because the two legs have different requirements. The
 * callback arrives from the external IdP as a cross-site top level navigation, which only reaches
 * the server when the cookie is {@code SameSite=Lax}. A tenant that sets its session cookies to
 * {@code Strict} would otherwise break linking outright, so this cookie does not follow the
 * tenant's {@code cookie_same_site} setting.
 *
 * @see AccountLinkingBrowserBinding
 */
public interface AccountLinkingCookieDelegate {

  /**
   * Issues the binding cookie when the browser is sent on to the external IdP.
   *
   * @param maxAgeSeconds should not outlive the linking session
   */
  void setBrowserBindingCookie(Tenant tenant, String secret, long maxAgeSeconds);

  /** Reads the binding secret presented by the browser at the callback. */
  Optional<String> getBrowserBindingSecret();

  /** Clears the cookie once the link is settled, whether it succeeded or failed. */
  void clearBrowserBindingCookie(Tenant tenant);
}
