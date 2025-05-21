package org.idp.server.authentication.interactors.device;

import org.idp.server.core.oidc.identity.device.AuthenticationDevice;
import org.idp.server.core.oidc.identity.device.NotificationChannel;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationDeviceNotifier {

  NotificationChannel chanel();

  void notify(
      Tenant tenant,
      AuthenticationDevice device,
      AuthenticationDeviceNotificationConfiguration configuration);
}
