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

import org.idp.server.platform.http.HttpQueryParams;

/**
 * Builds the URL the browser is sent back to at the end of the browser legs, on success or failure.
 *
 * <p>The return URI comes from the RP and may already carry a query string, so the separator has to
 * be decided rather than assumed — the same rule the authorization response applies when appending
 * to a registered redirect URI.
 *
 * <p>The return URI itself was matched against the client's allow-list when the link was started,
 * so sending a failure back to it is not an open redirect. What a failure carries is kept to an
 * error code: neither this server's description nor the external provider's response leaves.
 */
public class AccountLinkingReturnUri {

  String returnUri;
  HttpQueryParams params;

  AccountLinkingReturnUri(String returnUri, HttpQueryParams params) {
    this.returnUri = returnUri;
    this.params = params;
  }

  /** The callback parked the tokens; the RP completes the link with this state. */
  public static AccountLinkingReturnUri done(String returnUri, AccountLinkingState state) {
    HttpQueryParams params = new HttpQueryParams();
    params.add("linking", "done");
    params.add("state", state.value());
    return new AccountLinkingReturnUri(returnUri, params);
  }

  /**
   * The link failed after the browser was confirmed as the one that started it.
   *
   * <p>The state goes back so the RP can tell which link failed.
   */
  public static AccountLinkingReturnUri failed(
      String returnUri, AccountLinkingReturnError error, AccountLinkingState state) {
    HttpQueryParams params = new HttpQueryParams();
    params.add("linking", "failed");
    params.add("error", error.value());
    params.add("state", state.value());
    return new AccountLinkingReturnUri(returnUri, params);
  }

  /**
   * The link failed before this browser could be tied to the user who started it.
   *
   * <p>No state: the browser has not been shown to belong to that user, so nothing that identifies
   * the link is handed to whoever is holding it.
   */
  public static AccountLinkingReturnUri failedUnverified(
      String returnUri, AccountLinkingReturnError error) {
    HttpQueryParams params = new HttpQueryParams();
    params.add("linking", "failed");
    params.add("error", error.value());
    return new AccountLinkingReturnUri(returnUri, params);
  }

  public String value() {
    String separator = returnUri.contains("?") ? "&" : "?";

    return String.format("%s%s%s", returnUri, separator, params.params());
  }
}
