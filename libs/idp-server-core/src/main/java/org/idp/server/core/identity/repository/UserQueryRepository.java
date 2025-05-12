package org.idp.server.core.identity.repository;

import java.util.List;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface UserQueryRepository {

  User get(Tenant tenant, UserIdentifier userIdentifier);

  User findById(Tenant tenant, UserIdentifier userIdentifier);

  User findByEmail(Tenant tenant, String hint, String providerId);

  User findByPhone(Tenant tenant, String hint, String providerId);

  List<User> findList(Tenant tenant, int limit, int offset);

  User findByProvider(Tenant tenant, String providerId, String providerUserId);

  User findByAuthenticationDevice(Tenant tenant, String deviceId);
}
