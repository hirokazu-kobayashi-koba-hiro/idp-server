import { StepView } from "@/auth/types";

/** Common props passed to every config-driven step component. */
export type StepProps = {
  tenantId: string;
  id: string;
  step: StepView;
  /** Advance the flow (re-reads server state to resolve the next step). */
  onCompleted: () => void | Promise<void>;
};
