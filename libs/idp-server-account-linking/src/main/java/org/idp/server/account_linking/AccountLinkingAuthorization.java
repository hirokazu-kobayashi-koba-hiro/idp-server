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

/**
 * Outcome of {@code /linking/start}: where to send the browser, and the secret that browser must
 * carry back to the callback.
 */
public class AccountLinkingAuthorization {

  String authorizationUri;
  AccountLinkingBrowserBinding browserBinding;

  public AccountLinkingAuthorization(
      String authorizationUri, AccountLinkingBrowserBinding browserBinding) {
    this.authorizationUri = authorizationUri;
    this.browserBinding = browserBinding;
  }

  public String authorizationUri() {
    return authorizationUri;
  }

  public String browserBindingSecret() {
    return browserBinding.secret();
  }
}
