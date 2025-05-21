package org.idp.server.usecases.application.enduser;

import org.idp.server.core.identity.*;
import org.idp.server.core.identity.event.UserLifecycleEvent;
import org.idp.server.core.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.identity.event.UserLifecycleType;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.TokenEventPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.event.DefaultSecurityEventType;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class UserOperationEntryService implements UserOperationApi {

  UserCommandRepository userCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  TokenEventPublisher eventPublisher;
  UserLifecycleEventPublisher userLifecycleEventPublisher;

  public UserOperationEntryService(
      UserCommandRepository userCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      TokenEventPublisher eventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher) {
    this.userCommandRepository = userCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.eventPublisher = eventPublisher;
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
  }

  @Override
  public void delete(
      TenantIdentifier tenantIdentifier,
      User user,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    userCommandRepository.delete(tenant, user.userIdentifier());

    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);
    eventPublisher.publish(
        tenant, oAuthToken, DefaultSecurityEventType.user_delete, requestAttributes);
  }
}
