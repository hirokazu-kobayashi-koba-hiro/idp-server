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

import org.idp.server.account_linking.io.AccountLinkingResponse;
import org.idp.server.account_linking.io.AccountLinkingStartRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Account linking, split by which endpoints can carry a Bearer token.
 *
 * <p>{@code startLink}, {@code complete} and {@code findList} are called by the RP with a Bearer
 * token. {@code authorizeStart} and {@code handleCallback} are browser navigations that cannot
 * carry one, so they live outside the {@code /me} namespace and establish the subject by other
 * means.
 */
public interface AccountLinkingApi {

  AccountLinkingResponse startLink(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      ExternalIdpProvider provider,
      AccountLinkingStartRequest request,
      RequestAttributes requestAttributes);

  /** Browser navigation. The subject comes from the OP session, not from a Bearer token. */
  AccountLinkingResponse authorizeStart(
      TenantIdentifier tenantIdentifier,
      AccountLinkingState state,
      RequestAttributes requestAttributes);

  /** Browser navigation from the external IdP. Establishes nothing on its own. */
  AccountLinkingResponse handleCallback(
      TenantIdentifier tenantIdentifier,
      AccountLinkingState state,
      String code,
      String error,
      String errorDescription,
      RequestAttributes requestAttributes);

  AccountLinkingResponse complete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      AccountLinkingState state,
      RequestAttributes requestAttributes);

  AccountLinkingResponse findList(
      TenantIdentifier tenantIdentifier, User user, RequestAttributes requestAttributes);
}
