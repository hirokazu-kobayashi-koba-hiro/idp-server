/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
