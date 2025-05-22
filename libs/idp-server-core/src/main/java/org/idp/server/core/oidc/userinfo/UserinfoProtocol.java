/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.userinfo;

import org.idp.server.core.oidc.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public interface UserinfoProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
