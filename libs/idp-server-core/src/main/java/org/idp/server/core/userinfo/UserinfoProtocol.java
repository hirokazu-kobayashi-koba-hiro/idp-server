package org.idp.server.core.userinfo;

import org.idp.server.core.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestResponse;

public interface UserinfoProtocol {

  UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate);
}
