package org.idp.server.core.userinfo;

import org.idp.server.core.basic.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestResponse;

public interface UserinfoProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
