package org.idp.server.usecases.control_plane;

import java.util.List;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.control_plane.management.user.UserRegistrationContext;
import org.idp.server.control_plane.management.user.UserRegistrationContextCreator;
import org.idp.server.control_plane.management.user.UserManagementApi;
import org.idp.server.control_plane.management.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.user.io.UserRegistrationStatus;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;

@Transaction
public class UserManagementEntryService implements UserManagementApi {

  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;

  public UserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
  }

  @Override
  public UserManagementResponse register(TenantIdentifier tenantIdentifier, UserRegistrationRequest request) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    UserRegistrationContextCreator userRegistrationContextCreator = new UserRegistrationContextCreator(request);
    UserRegistrationContext context = userRegistrationContextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    userCommandRepository.register(tenant, context.user());

    return context.toResponse();
  }

  @Override
  public User get(TenantIdentifier tenantIdentifier, UserIdentifier userIdentifier) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return userQueryRepository.get(tenant, userIdentifier);
  }

  @Override
  public User findBy(TenantIdentifier tenantIdentifier, String email, String providerId) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    return userQueryRepository.findByEmail(tenant, email, providerId);
  }

  @Override
  public void update(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    userCommandRepository.update(tenant, user);
  }

  @Override
  public List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return userQueryRepository.findList(tenant, limit, offset);
  }
}
