/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.repository;

import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface CibaGrantRepository {

  void register(Tenant tenant, CibaGrant cibaGrant);

  void update(Tenant tenant, CibaGrant cibaGrant);

  CibaGrant find(Tenant tenant, AuthReqId authReqId);

  CibaGrant get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  void delete(Tenant tenant, CibaGrant cibaGrant);
}
