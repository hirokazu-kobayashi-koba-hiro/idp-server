/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.client;

public class Client {
  ClientIdentifier identifier;
  ClientName name;

  public Client() {}

  public Client(ClientIdentifier identifier, ClientName name) {
    this.identifier = identifier;
    this.name = name;
  }

  public ClientIdentifier identifier() {
    return identifier;
  }

  public ClientName name() {
    return name;
  }

  public boolean exists() {
    return identifier != null && !identifier.exists();
  }

  public String nameValue() {
    return name.value();
  }
}
