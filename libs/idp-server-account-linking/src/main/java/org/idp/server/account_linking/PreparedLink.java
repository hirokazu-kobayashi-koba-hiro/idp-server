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

/** The link to write, and for a re-link the row as it was before, holding the old tokens. */
public class PreparedLink {

  LinkedExternalAccount account;
  LinkedExternalAccount replaced;

  PreparedLink(LinkedExternalAccount account, LinkedExternalAccount replaced) {
    this.account = account;
    this.replaced = replaced;
  }

  public static PreparedLink created(LinkedExternalAccount account) {
    return new PreparedLink(account, null);
  }

  public static PreparedLink relink(LinkedExternalAccount replaced, LinkedExternalAccount account) {
    return new PreparedLink(account, replaced);
  }

  public LinkedExternalAccount account() {
    return account;
  }

  public LinkedExternalAccount replaced() {
    return replaced;
  }

  public boolean isRelink() {
    return replaced != null;
  }

  /**
   * Whether the tokens this re-link replaced are to be revoked at the external IdP.
   *
   * <p>Only a re-link replaces tokens, and only a provider set to {@code revoke_on_relink} has them
   * revoked.
   */
  public boolean revokesReplaced(AccountLinkingProviderConfiguration configuration) {
    return isRelink() && configuration.revokesOnRelink();
  }
}
