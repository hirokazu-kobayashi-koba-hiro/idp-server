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

package org.idp.server.core.extension.ciba.user;

import java.util.List;
import org.idp.server.basic.type.extension.Pairs;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class LoginHintResolver implements UserHintResolver {

  @Override
  public User resolve(
      Tenant tenant,
      UserHint userHint,
      UserHintRelatedParams userHintRelatedParams,
      UserQueryRepository userQueryRepository) {
    String loginHint = userHint.value();

    List<LoginHintMatcher> matchers =
        List.of(
            new PrefixMatcher(
                "sub:",
                hints -> {
                  UserIdentifier userIdentifier = new UserIdentifier(hints.getLeft());
                  return userQueryRepository.get(tenant, userIdentifier);
                }),
            new PrefixMatcher(
                "ex-sub:",
                hints ->
                    userQueryRepository.findByExternalIdpSubject(
                        tenant, hints.getLeft(), hints.getRight())),
            new PrefixMatcher(
                "device:",
                hints ->
                    userQueryRepository.findByDeviceId(tenant, hints.getLeft(), hints.getRight())),
            new PrefixMatcher(
                "phone:",
                hints ->
                    userQueryRepository.findByPhone(tenant, hints.getLeft(), hints.getRight())),
            new PrefixMatcher(
                "email:",
                hints ->
                    userQueryRepository.findByEmail(tenant, hints.getLeft(), hints.getRight())));

    return matchers.stream()
        .filter(matcher -> matcher.matches(loginHint))
        .findFirst()
        .map(
            matcher -> {
              Pairs<String, String> hints = matcher.extractHints(loginHint);
              return matcher.resolve(hints);
            })
        .orElse(User.notFound());
  }
}
