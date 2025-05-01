package org.idp.server.core.configuration;

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
