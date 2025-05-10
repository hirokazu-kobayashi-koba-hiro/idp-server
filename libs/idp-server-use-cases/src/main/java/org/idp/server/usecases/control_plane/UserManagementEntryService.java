package org.idp.server.usecases.control_plane;

import java.util.List;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.control_plane.user.UserManagementApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;

// TODO
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
  public void register(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    userCommandRepository.register(tenant, user);
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
