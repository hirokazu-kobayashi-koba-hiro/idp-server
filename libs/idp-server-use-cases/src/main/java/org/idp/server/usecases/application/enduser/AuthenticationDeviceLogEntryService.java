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

package org.idp.server.usecases.application.enduser;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceLogApi;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceLogEventPublisher;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceLogRequest;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceLogResponse;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationDeviceLogEntryService implements AuthenticationDeviceLogApi {

  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  AuthenticationDeviceLogEventPublisher eventPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthenticationDeviceLogEntryService.class);

  public AuthenticationDeviceLogEntryService(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      AuthenticationDeviceLogEventPublisher eventPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public AuthenticationDeviceLogResponse log(
      TenantIdentifier tenantIdentifier,
      AuthenticationDeviceLogRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    User user = findUser(tenant, request);

    if (user == null || !user.exists()) {
      log.debug(
          "Skipping security event publish - no user found for tenant: {}",
          tenantIdentifier.value());
      return AuthenticationDeviceLogResponse.ok();
    }

    eventPublisher.publish(
        tenant,
        user,
        DefaultSecurityEventType.authentication_device_log.toEventType(),
        request,
        requestAttributes);

    return AuthenticationDeviceLogResponse.ok();
  }

  private User findUser(Tenant tenant, AuthenticationDeviceLogRequest request) {
    if (request.hasDeviceId()) {
      User user = userQueryRepository.findByAuthenticationDevice(tenant, request.deviceId());
      if (user != null && user.exists()) {
        return user;
      }
    }

    if (request.hasUserId()) {
      User user = userQueryRepository.findById(tenant, new UserIdentifier(request.userId()));
      if (user != null && user.exists()) {
        return user;
      }
    }

    return null;
  }
}
