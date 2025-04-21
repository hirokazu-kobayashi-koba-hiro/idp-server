package org.idp.server.core.authentication.device;

import org.idp.server.core.oauth.identity.device.AuthenticationDevice;

public interface AuthenticationDeviceNotifier {

  void notify(AuthenticationDevice device);
}
