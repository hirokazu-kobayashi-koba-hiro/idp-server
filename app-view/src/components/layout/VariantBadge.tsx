import { Chip } from "@mui/material";

/**
 * Marks which set of authorization pages the request landed on.
 *
 * A canary release runs two page sets side by side, and the whole point of the rollout is knowing
 * which one a given request reached. The redirect target is decided server-side from
 * ui_config.variants, so without a marker on the page itself the two sets are indistinguishable
 * once rendered.
 */
export const VariantBadge = ({ label }: { label: string }) => {
  return (
    <Chip
      label={label}
      size="small"
      color="secondary"
      data-testid="view-variant-badge"
      sx={{
        position: "fixed",
        top: 12,
        right: 12,
        zIndex: 1300,
        fontWeight: "bold",
        letterSpacing: 0.5,
      }}
    />
  );
};
