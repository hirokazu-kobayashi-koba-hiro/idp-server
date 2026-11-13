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
package org.idp.server.account_linking.verifier;

import org.idp.server.account_linking.ProviderLinks;
import org.idp.server.account_linking.exception.AccountLinkingAccountLimitReachedException;

/**
 * A new link to a provider is added only while the user holds fewer than its {@code max_accounts}.
 *
 * <p>Checked for a new link only. Renewing one the user already holds adds nothing, and a limit
 * lowered below what a user holds leaves their links in place; only adding more is refused.
 *
 * <p>Two completions read the count before either has written, so linking twice at once can go one
 * past the limit. The limit bounds how many links a user collects over time; it is not a uniqueness
 * guarantee.
 */
public class LinkedAccountLimitVerifier {

  public void verify(ProviderLinks links, int maxAccounts) {
    if (links.count() >= maxAccounts) {
      throw new AccountLinkingAccountLimitReachedException(
          "As many accounts of this provider as allowed are already linked. Unlink one before"
              + " linking another.");
    }
  }
}
