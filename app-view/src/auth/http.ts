/**
 * Extracts a user-facing message from a failed interaction response.
 *
 * idp-server returns structured errors ({@code { error, error_description }}); prefer the
 * description, then the code, then the caller's generic fallback.
 */
export const readErrorMessage = async (
  response: Response,
  fallback: string,
): Promise<string> => {
  try {
    const body = await response.json();
    if (typeof body?.error_description === "string" && body.error_description) {
      return body.error_description;
    }
    if (typeof body?.error === "string" && body.error) {
      return body.error;
    }
  } catch {
    // non-JSON body — fall through to the generic message
  }
  return fallback;
};
