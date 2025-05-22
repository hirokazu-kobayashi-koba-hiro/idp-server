/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.Map;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;

public interface BackchannelAuthenticationRequestSqlExecutor {

  void insert(BackchannelAuthenticationRequest request);

  Map<String, String> selectOne(BackchannelAuthenticationRequestIdentifier identifier);

  void delete(BackchannelAuthenticationRequestIdentifier identifier);
}
