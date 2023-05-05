import axios from "axios";
export const get = async ({ url, headers }) => {
  try {
    return await axios.get(url, {
      maxRedirects: 0,
      headers,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const post = async ({ url, headers, body }) => {
  try {
    return await axios.post(url, body, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        ...headers,
        maxRedirects: 0,
      },
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const postWithJson = async ({ url, headers, body }) => {
  try {
    return await axios.post(url, body, {
      headers,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};
