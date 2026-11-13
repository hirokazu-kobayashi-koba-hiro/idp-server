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

import java.util.HashMap;
import java.util.Map;

/**
 * One provider a client may link, as the RP needs it to draw its linking screen.
 *
 * <p>Whether linking can start now, how many accounts the provider allows, and how many the user
 * already holds. A provider that is not configured or is disabled is reported as unavailable with
 * its links still counted: the user may hold links made before it was turned off, and can still see
 * and remove them.
 */
public class LinkingProviderSummary {

  ExternalIdpProvider provider;
  boolean available;
  boolean multipleAccounts;
  int maxAccounts;
  int linkedAccounts;

  LinkingProviderSummary(
      ExternalIdpProvider provider,
      boolean available,
      boolean multipleAccounts,
      int maxAccounts,
      int linkedAccounts) {
    this.provider = provider;
    this.available = available;
    this.multipleAccounts = multipleAccounts;
    this.maxAccounts = maxAccounts;
    this.linkedAccounts = linkedAccounts;
  }

  public static LinkingProviderSummary available(
      ExternalIdpProvider provider,
      AccountLinkingProviderConfiguration configuration,
      ProviderLinks links) {
    return new LinkingProviderSummary(
        provider,
        true,
        configuration.allowsMultipleAccounts(),
        configuration.maxAccounts(),
        links.count());
  }

  /** The settings are not read for an unavailable provider; only its links are counted. */
  public static LinkingProviderSummary unavailable(
      ExternalIdpProvider provider, ProviderLinks links) {
    return new LinkingProviderSummary(provider, false, false, 0, links.count());
  }

  public ExternalIdpProvider provider() {
    return provider;
  }

  public boolean isAvailable() {
    return available;
  }

  public int linkedAccounts() {
    return linkedAccounts;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("provider", provider.value());
    map.put("available", available);
    map.put("linked_accounts", linkedAccounts);
    if (available) {
      map.put("multiple_accounts", multipleAccounts);
      map.put("max_accounts", maxAccounts);
    }
    return map;
  }
}
