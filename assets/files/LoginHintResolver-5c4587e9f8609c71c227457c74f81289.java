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
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.Pairs;

public class LoginHintResolver implements UserHintResolver {

  LoggerWrapper log = LoggerWrapper.getLogger(this.getClass());

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
                  log.debug("Backchannel Authentication Resolving user hint  sub");
                  UserIdentifier userIdentifier = new UserIdentifier(hints.getLeft());
                  return userQueryRepository.findById(tenant, userIdentifier);
                }),
            new PrefixMatcher(
                "ex-sub:",
                hints -> {
                  log.debug("Backchannel Authentication Resolving user hint ex-sub");
                  return userQueryRepository.findByExternalIdpSubject(
                      tenant, hints.getLeft(), hints.getRight());
                }),
            new PrefixMatcher(
                "device:",
                hints -> {
                  log.debug("Backchannel Authentication Resolving user hint device");
                  return userQueryRepository.findByDeviceId(
                      tenant,
                      new AuthenticationDeviceIdentifier(hints.getLeft()),
                      hints.getRight());
                }),
            new PrefixMatcher(
                "phone:",
                hints -> {
                  log.debug("Backchannel Authentication Resolving user hint phone");
                  return userQueryRepository.findByPhone(tenant, hints.getLeft(), hints.getRight());
                }),
            new PrefixMatcher(
                "email:",
                hints -> {
                  log.debug("Backchannel Authentication Resolving user hint email");
                  return userQueryRepository.findByEmail(tenant, hints.getLeft(), hints.getRight());
                }));

    User user =
        matchers.stream()
            .filter(matcher -> matcher.matches(loginHint))
            .findFirst()
            .map(
                matcher -> {
                  Pairs<String, String> hints = matcher.extractHints(loginHint);
                  return matcher.resolve(hints);
                })
            .orElseGet(
                () -> {
                  log.warn(
                      "Backchannel Authentication login_hint does not match any supported format: {}",
                      loginHint);
                  return User.notFound();
                });

    if (!user.exists()) {
      log.warn("Backchannel Authentication user not found for login_hint: {}", loginHint);
    }

    return user;
  }
}
