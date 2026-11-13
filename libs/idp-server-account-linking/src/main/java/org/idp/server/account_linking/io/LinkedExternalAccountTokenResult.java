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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.account_linking.ExternalAccessTokenAcquisition;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Outcome of a stored token retrieval.
 *
 * <p>Wraps the answer with what happened on the way, which the caller records as events of their
 * own: a refresh is worth recording even when the retrieval succeeds, and a refused refresh means
 * the user has to link again.
 */
public class LinkedExternalAccountTokenResult {

  AccountLinkingResult result;
  boolean refreshed;
  boolean relinkRequired;
  ExternalIdpProvider provider;
  String error;

  public LinkedExternalAccountTokenResult(
      AccountLinkingResult result,
      boolean refreshed,
      boolean relinkRequired,
      ExternalIdpProvider provider,
      String error) {
    this.result = result;
    this.refreshed = refreshed;
    this.relinkRequired = relinkRequired;
    this.provider = provider;
    this.error = error;
  }

  /** The stored access token handed to the client. */
  public static LinkedExternalAccountTokenResult retrieved(
      ExternalAccessTokenAcquisition acquisition,
      ExternalIdpProvider provider,
      RequestedClientId requestedClientId,
      User user) {
    AccountLinkingResult result =
        AccountLinkingResult.success(
                AccountLinkingStatus.OK,
                acquisition.accessToken().toResponse(SystemDateTime.now()),
                DefaultSecurityEventType.external_account_token_retrieved,
                user)
            .withContext(requestedClientId, user);
    return new LinkedExternalAccountTokenResult(
        result, acquisition.isRefreshed(), false, provider, null);
  }

  /**
   * The retrieval refused.
   *
   * @param provider the account's provider, or {@code null} when it was refused before the account
   *     was found
   */
  public static LinkedExternalAccountTokenResult refused(
      LinkedExternalAccountTokenException exception,
      RequestedClientId requestedClientId,
      User user,
      ExternalIdpProvider provider) {
    AccountLinkingResult result =
        AccountLinkingResult.error(
                exception.status(),
                exception.error(),
                exception.getMessage(),
                DefaultSecurityEventType.external_account_token_retrieval_failed)
            .withContext(requestedClientId, user);
    return new LinkedExternalAccountTokenResult(
        result, false, exception.isRelinkRequired(), provider, exception.error());
  }

  public AccountLinkingResult result() {
    return result;
  }

  public boolean refreshed() {
    return refreshed;
  }

  public boolean relinkRequired() {
    return relinkRequired;
  }

  /**
   * What the events of this retrieval carry: the provider, once the account was found, and the
   * error code of a refusal.
   *
   * <p>The provider lets a monitor count failures per provider, which tells a provider that revoked
   * its grants from one client probing for tokens. The alias is left out: it identifies the user's
   * link and is not needed to count.
   */
  public Map<String, Object> eventDetails() {
    Map<String, Object> details = new HashMap<>();
    if (provider != null && provider.exists()) {
      details.put("provider", provider.value());
    }
    if (error != null) {
      details.put("error", error);
    }
    return details;
  }
}
