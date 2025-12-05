import axios from "axios";
import { wrapper } from "axios-cookiejar-support";
import { CookieJar } from "tough-cookie";

const jar = new CookieJar();
const client = wrapper(axios.create({ jar, withCredentials: true }));


export const get = async ({ url, headers }) => {
  try {
    return await client.get(url, {
      maxRedirects: 0,
      headers,
      withCredentials: true,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const post = async ({ url, headers, body }) => {
  try {
    return await client.post(url, body, {
      maxRedirects: 0,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        ...headers,
      },
      withCredentials: true
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const postWithJson = async ({ url, headers, body }) => {
  try {
    return await client.post(url, body, {
      maxRedirects: 0,
      headers,
      withCredentials: true
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const putWithJson = async ({ url, headers, body }) => {
  try {
    return await client.put(url, body, {
      headers,
      withCredentials: true
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const patchWithJson = async ({ url, headers, body }) => {
  try {
    return await client.patch(url, body, {
      headers,
      withCredentials: true
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

export const deletion = async ({ url, headers }) => {
  try {
    return await client.delete(url, {
      headers,
      withCredentials: true
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};
