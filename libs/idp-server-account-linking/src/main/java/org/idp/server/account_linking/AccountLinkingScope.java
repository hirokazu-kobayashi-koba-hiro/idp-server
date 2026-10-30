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

import java.util.List;
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
public enum AccountLinkingScope {

  /** See the user's linked accounts. */
  READ("linked_account:read"),

  /** Start, complete and remove links. Includes {@link #READ}. */
  MANAGE("linked_account:manage"),

  /** Retrieve the access token stored for a linked account. */
  READ_TOKEN("linked_account:read_token");

  String value;

  AccountLinkingScope(String value) {
    this.value = value;
  }

  /** The scope value as it appears in a token. */
  public String value() {
    return value;
  }

  /** Whether the token carries this scope, or one that includes it. */
  public boolean isGrantedBy(OAuthToken oAuthToken) {
    List<String> scopes = oAuthToken.scopeAsList();
    return switch (this) {
      case READ -> scopes.contains(READ.value) || scopes.contains(MANAGE.value);
      case MANAGE, READ_TOKEN -> scopes.contains(value);
    };
  }
}
