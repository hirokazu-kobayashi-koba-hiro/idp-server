package org.idp.server.core.notification;

import org.idp.server.core.type.exception.UnSupportedException;

public enum EmailSenderType {
  SMTP("smtp"),
  EXTERNAL_API_SERVICE("external_api_service");

  String typeName;

  EmailSenderType(String typeName) {
    this.typeName = typeName;
  }

  public static EmailSenderType of(String type) {
    for (EmailSenderType senderType : values()) {
      if (senderType.typeName.equals(type)) {
        return senderType;
      }
    }
    throw new UnSupportedException("No EmailSenderType found for type " + type);
  }
}
