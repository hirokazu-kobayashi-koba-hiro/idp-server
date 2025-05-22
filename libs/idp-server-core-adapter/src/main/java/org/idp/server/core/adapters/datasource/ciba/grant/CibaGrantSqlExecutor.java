/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.Map;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;

public interface CibaGrantSqlExecutor {

  void insert(CibaGrant cibaGrant);

  void update(CibaGrant cibaGrant);

  Map<String, String> selectOne(AuthReqId authReqId);

  Map<String, String> selectOne(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  void delete(CibaGrant cibaGrant);
}
