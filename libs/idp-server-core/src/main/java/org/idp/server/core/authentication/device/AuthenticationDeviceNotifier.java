package org.idp.server.core.authentication.device;

import org.idp.server.core.notification.push.PushNotificationChannel;
import org.idp.server.core.oauth.identity.device.AuthenticationDevice;

public interface AuthenticationDeviceNotifier {

  PushNotificationChannel chanel();

  void notify(AuthenticationDevice device);
}
