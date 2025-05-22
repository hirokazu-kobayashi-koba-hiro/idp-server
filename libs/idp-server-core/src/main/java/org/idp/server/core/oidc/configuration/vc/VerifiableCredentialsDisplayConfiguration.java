/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.configuration.vc;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class VerifiableCredentialsDisplayConfiguration implements JsonReadable {
  String name;
  String locale;
  List<Map<String, String>> logo;
  String description;
  String backgroundColor;
  String textColor;
}
