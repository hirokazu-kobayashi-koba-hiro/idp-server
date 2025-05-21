package org.idp.server.core.oidc.userinfo;

import org.idp.server.core.oidc.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public interface UserinfoProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
