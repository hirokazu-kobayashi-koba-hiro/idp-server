import { faker } from "@faker-js/faker";

export const convertNextAction = (redirectUri) => {
  const query = redirectUri.includes("?")
    ? redirectUri.split("?")[1]
    : redirectUri.split("#")[1];

  const params = new URLSearchParams(query);
  const nextAction = params.get("id") ? "goAuthentication": "goNext";

  return {
    nextAction: nextAction,
    params,
  };
};

export const convertToAuthorizationResponse = (redirectUri) => {

  const query = redirectUri.includes("?")
    ? redirectUri.split("?")[1]
    : redirectUri.split("#")[1];
  const params = new URLSearchParams(query);

  return {
    responseMode: redirectUri.includes("?") ? "?" : "#",
    code: params.get("code"),
    accessToken: params.get("access_token"),
    tokenType: params.get("token_type"),
    refreshToken: params.get("refresh_token"),
    idToken: params.get("id_token"),
    expiresIn: params.get("expires_in"),
    scope: params.get("scope"),
    state: params.get("state"),
    iss: params.get("iss"),
    response: params.get("response"),
    vpToken: params.get("vp_token"),
    presentationSubmission: params.get("presentation_submission"),
    error: params.get("error"),
    errorDescription: params.get("error_description"),
  };
};

export const createBasicAuthHeader = ({ username, password }) => {
  const basicParam = `${username}:${password}`;
  return {
    Authorization: `Basic ${Buffer.from(basicParam).toString("base64")}`,
  };
};

export const createBearerHeader = (accessToken) => {
  return {
    Authorization: `Bearer ${accessToken}`,
  };
};

export const toEpocTime = ({ adjusted = 0 }) => {
  return Math.floor(new Date().getTime() / 1000) + adjusted;
};

export const convertToSnake = (params) => {
  if (isObject(params)) {
    const convertParams = {};
    Object.keys(params).map((key) => {
      convertParams[toSnake(key)] = convertToSnake(params[key]);
    });
    return convertParams;
  } else if (isArray(params)) {
    return params.map((value) => {
      return convertToSnake(value);
    });
  }
  return params;
};

export const toSnake = (params) => {
  return params.replace(/[A-Z]/g, (s) => {
    return "_" + s.toLowerCase();
  });
};

export const isObject = (value) => {
  return value !== null && typeof value === "object" && !isArray(value);
};

export const isArray = (value) => {
  if (!value) {
    return false;
  }
  return Array.isArray(value);
};

export const isString = (value) => {
  return typeof value === "string" || value instanceof String;
};

export const isNumber = (value) => {
  return typeof value === "number" || value instanceof Number;
};

export const isBoolean = (value) => {
  return typeof value === "boolean" || value instanceof Boolean;
};

export const sleep = (m) => {
  return new Promise((resolve) => setTimeout(resolve, m));
};

export const matchWithUSASCII = (value) => {
  // eslint-disable-next-line no-control-regex
  const asciiRegex = new RegExp("^[\x00-\x7F]*$");
  return asciiRegex.test(value);
};

export const base64UrlEncode = (input) => {
  if (Buffer.isEncoding("base64url")) {
    return input.toString("base64url");
  } else {
    const fromBase64 = (base64) =>
      base64.replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
    return fromBase64(input.toString("base64"));
  }
};

export const toJsonString = (object) => {
  return JSON.stringify(object, null, 2);
};

export const generatePassword = (length) => {
  const upper = faker.string.alpha({ length: 1, casing: "upper" }); // ✅ v7以降
  const digit = faker.string.numeric(1);
  const symbol = faker.helpers.arrayElement(["!", "@", "#", "$", "%", "^", "&", "*", "(", ")"]);
  const rest = faker.string.alphanumeric(length);

  const raw = upper + digit + symbol + rest;
  return faker.helpers.shuffle([...raw]).join("");
};

export const validateSchemeDefinition = ({ name, target, schemeDefinition }) => {
  let errors = [];
  Object.entries(schemeDefinition).forEach(([key, definition]) => {
    console.log(key);
    console.log(definition);
    const element = target[key];
    const objectName = `${name}.${key}`;
    if (definition.required && element === undefined) {
      errors = [...errors, `${element} is required`];
      return;
    }
    try {
      if (definition.type && typeof element !== definition.type) {
        errors = [...errors, `${objectName}' does not match, defined type: ${definition.type}`];
        return;
      }
    } catch (e) {
      errors = [...errors, `${objectName}' does not match, defined type: ${definition.type}`];
      return;
    }
    try {
      if (definition.values && !definition.values.includes(element)) {
        errors = [...errors, `${objectName}'s value is not allowed, it must be one of ${definition.values.join(",")}`];
        return;
      }
    } catch (e) {
      errors = [...errors, `${objectName}'s value is not allowed, it must be one of ${definition.values.join(",")}`];
      return;
    }
    try {
      if (isObject(element)) {
        console.log(element.schema);
        const leafErrors = validateSchemeDefinition({
          name: key,
          target: element,
          schemeDefinition: definition.schema
        });
        errors = [...errors, ...leafErrors];
      }
    } catch (e) {
      console.log(e);
    }
  });
  return errors;
};
