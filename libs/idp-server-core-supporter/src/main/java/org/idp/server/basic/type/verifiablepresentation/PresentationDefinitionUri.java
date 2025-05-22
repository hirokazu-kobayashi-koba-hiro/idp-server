/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.verifiablepresentation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class PresentationDefinitionUri {
  String value;

  public PresentationDefinitionUri() {}

  public PresentationDefinitionUri(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PresentationDefinitionUri that = (PresentationDefinitionUri) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public URI toURI() throws URISyntaxException {
    return new URI(value);
  }
}
