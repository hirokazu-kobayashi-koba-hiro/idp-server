/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba;

import org.idp.server.core.extension.ciba.handler.io.*;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface CibaProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  CibaRequestResult request(CibaRequest request);

  CibaIssueResponse issueResponse(CibaIssueRequest cibaIssueRequest);

  BackchannelAuthenticationRequest get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier);

  CibaAuthorizeResponse authorize(CibaAuthorizeRequest request);

  CibaDenyResponse deny(CibaDenyRequest request);
}
