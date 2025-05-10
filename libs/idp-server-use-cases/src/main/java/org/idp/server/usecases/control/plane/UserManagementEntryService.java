package org.idp.server.usecases.control.plane;

import java.util.List;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.control.plane.UserManagementApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;

// TODO
@Transaction
public class UserManagementEntryService implements UserManagementApi {

  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;

  public UserManagementEntryService(
      TenantQueryRepository tenantQueryRepository, UserQueryRepository userQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
  }

  @Override
  public void register(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    userQueryRepository.register(tenant, user);
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
    userQueryRepository.update(tenant, user);
  }

  @Override
  public List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return userQueryRepository.findList(tenant, limit, offset);
  }
}
