export const convertToAuthorizationResponse = (redirectUri) => {
  const query = redirectUri.includes("?")
    ? redirectUri.split("?")[1]
    : redirectUri.split("#"[1]);
  const params = new URLSearchParams(query);
  return {
    responseMode: redirectUri.includes("?") ? "?" : "#",
    code: params.get("code"),
    accessToken: params.get("access_token"),
    tokenType: params.get("token_type"),
    refreshToken: params.get("refresh_token"),
    idToken: params.get("id_token"),
    expiresIn: params.get("expires_in"),
    state: params.get("state"),
    iss: params.get("iss"),
    error: params.get("error"),
    errorDescription: params.get("error_description")
  };
};

export const createBasicAuthHeader = ({username, password}) => {
  const basicParam = `${username}:${password}`;
  return {
    Authorization: `Basic  ${Buffer.from(basicParam).toString("base64")}`,
  };
};

export const convertToSnake = (params) => {
  if (isObject(params)) {
    const convertParams = {};
    Object.keys(params).map(key => {
      convertParams[toSnake(key)] = convertToSnake(params[key]);
    });
    return convertParams;
  } else if (isArray(params)) {
    return params.map(value => {
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

const isObject = (value) => {
  return value !== null && typeof value === "object" && !isArray(value);
};

const isArray = (value) => {
  if (!value) {
    return false;
  }
  return Array.isArray(value);
};