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

import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * The tenant's error page, for a browser leg that cannot send the browser back to the RP.
 *
 * <p>Used when the link cannot be identified, or the browser is not the one that started it. In the
 * first case there is no return URI to send it to; in the second there is one, but the browser is
 * not its owner's. The authorization endpoint sends errors it cannot redirect to the same page.
 *
 * <p>The description is fixed per kind of failure. Exception messages carry the state and the
 * provider, which do not belong in a URL.
 */
public class AccountLinkingErrorPage {

  Tenant tenant;
  AccountLinkingStatus status;

  public AccountLinkingErrorPage(Tenant tenant, AccountLinkingStatus status) {
    this.tenant = tenant;
    this.status = status;
  }

  public String value() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("error", error());
    params.add("error_description", description());
    params.add("tenant_id", tenant.identifier().value());

    String base = tenant.baseUrl();
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return String.format("%s/error/?%s", normalizedBase, params.params());
  }

  private String error() {
    return switch (status) {
      case FORBIDDEN -> "access_denied";
      case BAD_REQUEST, NOT_FOUND, CONFLICT -> "invalid_request";
      default -> "server_error";
    };
  }

  private String description() {
    return switch (status) {
      case FORBIDDEN ->
          "This account link was started in another browser or by another user. Start linking again from the application.";
      case BAD_REQUEST, NOT_FOUND, CONFLICT ->
          "This account link is no longer valid. Start linking again from the application.";
      default -> "Account linking could not be completed. Try again later.";
    };
  }
}
