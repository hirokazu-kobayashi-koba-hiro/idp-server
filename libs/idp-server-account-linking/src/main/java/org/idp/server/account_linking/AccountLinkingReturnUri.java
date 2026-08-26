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
 * Builds the URL the browser is sent back to once the callback has parked the tokens.
 *
 * <p>The return URI comes from the RP and may already carry a query string, so the separator has to
 * be decided rather than assumed — the same rule the authorization response applies when appending
 * to a registered redirect URI.
 */
public class AccountLinkingReturnUri {

  String returnUri;
  AccountLinkingState state;

  public AccountLinkingReturnUri(String returnUri, AccountLinkingState state) {
    this.returnUri = returnUri;
    this.state = state;
  }

  public String value() {
    HttpQueryParams params = new HttpQueryParams();
    params.add("linking", "done");
    params.add("state", state.value());

    String separator = returnUri.contains("?") ? "&" : "?";

    return String.format("%s%s%s", returnUri, separator, params.params());
  }
}
