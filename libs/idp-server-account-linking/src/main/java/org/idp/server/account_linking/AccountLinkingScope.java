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

import org.idp.server.core.openid.token.OAuthToken;

/**
 * The scopes a user grants a client over their linked external accounts.
 *
 * <p>Split the way account consoles split them: seeing the links is one grant, and adding or
 * removing them is another that includes seeing them, since a client that manages links has to list
 * them to know what to remove. Adding and removing are not split further; no client manages one
 * without the other.
 *
 * <p>Handing out the stored tokens is a separate grant again ({@link #READ_TOKEN}). Neither of the
 * others implies it, and it implies neither of them.
 */
public class AccountLinkingScope {

  /** See the user's linked accounts. */
  public static final String READ = "linked_account:read";

  /** Start, complete and remove links. Includes {@link #READ}. */
  public static final String MANAGE = "linked_account:manage";

  /** Retrieve the access token stored for a linked account. */
  public static final String READ_TOKEN = "linked_account:read_token";

  public static boolean allowsRead(OAuthToken oAuthToken) {
    return oAuthToken.scopeAsList().contains(READ) || allowsManage(oAuthToken);
  }

  public static boolean allowsManage(OAuthToken oAuthToken) {
    return oAuthToken.scopeAsList().contains(MANAGE);
  }
}
