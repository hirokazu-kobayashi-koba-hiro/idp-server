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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationRule;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * What a linking provider's configuration ({@code type: account-linking}) must hold.
 *
 * <p>Checked when it is registered or updated, so a configuration that would fail only when a user
 * tries to link — a missing endpoint, a callback that is not this provider's, a duplicate policy
 * misspelt into the default — is refused up front.
 *
 * <p>Links name their provider by {@code sso_provider}. Renaming it, or deleting the configuration
 * while links remain, would leave their stored tokens neither retrievable nor revocable, so the
 * name is fixed and a deletion waits until the links are gone.
 */
public class AccountLinkingFederationConfigurationRule implements FederationConfigurationRule {

  static final List<String> REQUIRED =
      List.of("authorization_endpoint", "token_endpoint", "client_id", "redirect_uri");

  /**
   * The longest {@code refresh_before_expiry_seconds} accepted.
   *
   * <p>A margin as long as the access token's lifetime refreshes on every retrieval. The lifetime
   * comes from each token response, so it cannot be checked here; this only bounds the setting.
   */
  static final long MAX_REFRESH_BEFORE_EXPIRY_SECONDS = 3600;

  LinkedExternalAccountQueryRepository accountQueryRepository;

  public AccountLinkingFederationConfigurationRule(
      LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.accountQueryRepository = accountQueryRepository;
  }

  @Override
  public FederationType type() {
    return AccountLinkingConfigurationResolver.FEDERATION_TYPE;
  }

  @Override
  public List<String> problems(FederationConfiguration configuration) {
    Map<String, Object> payload =
        configuration.payload() == null ? Map.of() : configuration.payload();
    List<String> problems = new ArrayList<>();

    for (String key : REQUIRED) {
      if (!hasText(payload.get(key))) {
        problems.add("payload." + key + " is required.");
      }
    }

    // The callback is per provider: a response is matched to its link by the provider it names.
    Object redirectUri = payload.get("redirect_uri");
    String provider =
        configuration.ssoProvider() == null ? null : configuration.ssoProvider().name();
    if (hasText(redirectUri)
        && provider != null
        && !redirectUri.toString().endsWith("/v1/linking/callback/" + provider)) {
      problems.add(
          "payload.redirect_uri must be this provider's callback, /{tenant-id}/v1/linking/callback/"
              + provider
              + ".");
    }

    Object policy = payload.get("duplicate_link_policy");
    if (policy != null && !DuplicateLinkPolicy.isKnown(policy.toString())) {
      problems.add("payload.duplicate_link_policy must be reject or allow.");
    }

    Object multipleAccounts = payload.get("multiple_accounts");
    if (multipleAccounts != null && !(multipleAccounts instanceof Boolean)) {
      problems.add("payload.multiple_accounts must be true or false.");
    }

    Object revokeOnRelink = payload.get("revoke_on_relink");
    if (revokeOnRelink != null && !(revokeOnRelink instanceof Boolean)) {
      problems.add("payload.revoke_on_relink must be true or false.");
    }

    Object refreshBeforeExpiry = payload.get("refresh_before_expiry_seconds");
    if (refreshBeforeExpiry != null
        && !isSecondsUpTo(refreshBeforeExpiry, MAX_REFRESH_BEFORE_EXPIRY_SECONDS)) {
      problems.add(
          "payload.refresh_before_expiry_seconds must be an integer from 0 to "
              + MAX_REFRESH_BEFORE_EXPIRY_SECONDS
              + ".");
    }

    // With openid the external account is identified by the id_token's sub, which needs the
    // issuer to compare and the keys to verify.
    Object scopes = payload.get("scopes_supported");
    if (scopes instanceof List<?> list && list.contains("openid")) {
      if (!hasText(payload.get("issuer"))) {
        problems.add("payload.issuer is required when scopes_supported includes openid.");
      }
      if (!hasText(payload.get("jwks_uri"))) {
        problems.add("payload.jwks_uri is required when scopes_supported includes openid.");
      }
    }
    return problems;
  }

  @Override
  public Map<String, Long> dependents(Tenant tenant, FederationConfiguration configuration) {
    long links =
        accountQueryRepository.findTotalCount(
            tenant,
            new LinkedExternalAccountQueries(
                Map.of("provider", configuration.ssoProvider().name())));
    return links > 0 ? Map.of("linked_external_accounts", links) : Map.of();
  }

  @Override
  public boolean allowsRename() {
    return false;
  }

  private static boolean isSecondsUpTo(Object value, long max) {
    if (!(value instanceof Integer) && !(value instanceof Long)) {
      return false;
    }
    long seconds = ((Number) value).longValue();
    return seconds >= 0 && seconds <= max;
  }

  private static boolean hasText(Object value) {
    return value != null && !value.toString().isBlank();
  }
}
