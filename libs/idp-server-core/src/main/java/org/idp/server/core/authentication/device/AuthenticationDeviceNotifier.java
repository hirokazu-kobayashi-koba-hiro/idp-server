package org.idp.server.core.authentication.device;

import org.idp.server.core.authentication.notification.device.NotificationChannel;
import org.idp.server.core.identity.device.AuthenticationDevice;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationDeviceNotifier {

  NotificationChannel chanel();

  void notify(Tenant tenant, AuthenticationDevice device, AuthenticationDeviceNotificationConfiguration configuration);
}
