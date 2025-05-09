package org.idp.server.usecases.application;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.identity.*;
import org.idp.server.core.identity.event.UserLifecycleEvent;
import org.idp.server.core.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.identity.event.UserLifecycleType;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.security.event.TokenEventPublisher;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class UserOperationEntryService implements UserOperationApi {

  UserCommandRepository userCommandRepository;
  TenantRepository tenantRepository;
  TokenEventPublisher eventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;

  public UserOperationEntryService(
      UserCommandRepository userCommandRepository,
      TenantRepository tenantRepository,
      TokenEventPublisher eventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher) {
    this.userCommandRepository = userCommandRepository;
    this.tenantRepository = tenantRepository;
    this.eventPublisher = eventPublisher;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
  }

  @Override
  public void delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    userCommandRepository.delete(tenant, user.userIdentifier());

    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);
    eventPublisher.publish(
        tenant, oAuthToken, DefaultSecurityEventType.user_delete, requestAttributes);
  }
}
