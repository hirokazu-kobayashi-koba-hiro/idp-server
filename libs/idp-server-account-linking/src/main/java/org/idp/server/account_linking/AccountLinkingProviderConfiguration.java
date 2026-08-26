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

import org.idp.server.federation.sso.oidc.OidcSsoConfiguration;

/**
 * The linking provider's settings, resolved once.
 *
 * <p>Two views of the same {@code federation_configurations} row: the OIDC client settings the
 * federation module already understands, and the handful of fields linking adds on top. They are
 * kept together so a caller resolves the provider once rather than reading the row per view.
 *
 * <p>A dedicated configuration type is what this should eventually become. Login and linking want
 * different scopes and different redirect URIs, so sharing a row makes the login consent screen
 * advertise API scopes.
 */
public class AccountLinkingProviderConfiguration {

  OidcSsoConfiguration oidc;
  AccountLinkingConfiguration linking;

  public AccountLinkingProviderConfiguration(
      OidcSsoConfiguration oidc, AccountLinkingConfiguration linking) {
    this.oidc = oidc;
    this.linking = linking;
  }

  public OidcSsoConfiguration oidc() {
    return oidc;
  }

  public DuplicateLinkPolicy duplicateLinkPolicy() {
    return linking.duplicateLinkPolicy();
  }
}
