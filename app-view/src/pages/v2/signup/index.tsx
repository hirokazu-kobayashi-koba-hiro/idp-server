import SignUpPage from "@/pages/signup";
import { VariantBadge } from "@/components/layout/VariantBadge";

/**
 * The v2 sign-up page. See {@link ../signin/index.tsx} for why the default page is reused.
 *
 * Exists so a variant can declare both pages of its own scheme. A variant that names signin_page
 * but not signup_page sends prompt=create back to the default deployment, which is correct but
 * leaves the v2 sign-up path untested.
 */
export default function SignUpV2() {
  return (
    <>
      <VariantBadge label="v2" />
      <SignUpPage />
    </>
  );
}
