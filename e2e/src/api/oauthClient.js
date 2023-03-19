import { get } from "lib/http"

export const getAuthorizations = async ({
  endpoint,
  clientId,
  responseType,
}) => {
  return await get({
    url: "",
    headers: {},
  })
}
