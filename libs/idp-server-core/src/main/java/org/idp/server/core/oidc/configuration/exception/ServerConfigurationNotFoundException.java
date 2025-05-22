/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.configuration.exception;

import org.idp.server.platform.exception.NotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** ServerConfigurationNotFoundException */
public class ServerConfigurationNotFoundException extends NotFoundException {

  Tenant tenant;

  public ServerConfigurationNotFoundException(String message, Tenant tenant) {
    super(message);
    this.tenant = tenant;
  }

  public Tenant tenant() {
    return tenant;
  }
}
