package org.idp.server.core.protocol;

import org.idp.server.core.handler.userinfo.UserinfoDelegate;
import org.idp.server.core.handler.userinfo.io.UserinfoRequest;
import org.idp.server.core.handler.userinfo.io.UserinfoRequestResponse;

public interface UserinfoProtocol {

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
