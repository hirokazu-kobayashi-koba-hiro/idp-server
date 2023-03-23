
export const convertToSnake = (params) => {
  if (isObject(params)) {
    const convertParams = {};
    Object.keys(params).map(key => {
      convertParams[toSnake(key)] = convertToSnake(params[key])
    })
    return convertParams;
  } else if (isArray(params)) {
    return params.map(value => {
      return convertToSnake(value)
    })
  }
  return params;
}

export const toSnake = (params) => {
  return params.replace(/[A-Z]/g, (s) => {
    return '_' + s.toLowerCase();
  });
}

const isObject = (value) => {
  return value !== null && typeof value === "object" && !isArray(value);
}

const isArray = (value) => {
  if (!value) {
    return false;
  }
  return Array.isArray(value);
}