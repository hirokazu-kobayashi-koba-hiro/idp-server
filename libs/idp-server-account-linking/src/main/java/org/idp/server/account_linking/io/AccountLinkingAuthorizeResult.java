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

package org.idp.server.account_linking.io;

/**
 * Outcome of {@code /linking/start}: the result to answer with, and — on success — the secret the
 * browser must carry back to the callback.
 *
 * <p>This phase alone answers with more than a {@link AccountLinkingResult}, because it decides
 * that a binding cookie is due while the entry service is the one that can write it.
 */
public class AccountLinkingAuthorizeResult {

  AccountLinkingResult result;
  String browserBindingSecret;

  AccountLinkingAuthorizeResult(AccountLinkingResult result, String browserBindingSecret) {
    this.result = result;
    this.browserBindingSecret = browserBindingSecret;
  }

  public static AccountLinkingAuthorizeResult of(
      AccountLinkingResult result, String browserBindingSecret) {
    return new AccountLinkingAuthorizeResult(result, browserBindingSecret);
  }

  public static AccountLinkingAuthorizeResult error(AccountLinkingResult result) {
    return new AccountLinkingAuthorizeResult(result, null);
  }

  public AccountLinkingResult result() {
    return result;
  }

  public String browserBindingSecret() {
    return browserBindingSecret;
  }

  public boolean hasBrowserBinding() {
    return browserBindingSecret != null && !browserBindingSecret.isEmpty();
  }
}
