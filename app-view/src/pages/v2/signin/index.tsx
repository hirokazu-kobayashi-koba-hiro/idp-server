import SignIn from "@/pages/signin";
import { VariantBadge } from "@/components/layout/VariantBadge";

/**
 * The v2 sign-in page, used to verify request-scoped view routing (#1830).
 *
 * Reuses the default page rather than copying it: what needs verifying is which URL the
 * authorization endpoint redirects to, not a second implementation of the sign-in flow. The badge
 * is the only difference, and it is what makes the landing visible in a browser and assertable in
 * an E2E test.
 */
export default function SignInV2() {
  return (
    <>
      <VariantBadge label="v2" />
      <SignIn />
    </>
  );
}
