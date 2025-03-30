import ReactMarkdown from "react-markdown";
import { Box, Container, Paper, useMediaQuery, useTheme } from "@mui/material";

const Privacy = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const markdown = `
# IdP Service Terms of Use

## Article 1 (Rights and Jurisdiction)

1. These Terms of Use (hereinafter referred to as "these Terms") define the relationship between the operator (hereinafter referred to as "Operator") and users regarding the use of the IdP Service (hereinafter referred to as "this Service").
2. By using this Service, users are deemed to have agreed to these Terms.

## Article 2 (Formation of Contract)

1. When a user starts using this Service, it is considered that they have agreed to these Terms, and a contract based on these Terms is established between the user and the Operator.
2. The contract based on these Terms remains valid as long as the user continues to use this Service.

## Article 3 (Terms of Use)

1. Users must use this Service properly and must not engage in illegal or prohibited activities.
2. Users must not use this Service for unauthorized access to other services or for improper purposes.

## Article 4 (Account Management)

1. Users must appropriately manage their account information.
2. If unauthorized use by a third party is discovered, the user must promptly notify the Operator.

## Article 5 (Acquisition of Transaction Information)

1. The Operator collects the necessary information for user authentication.
2. The collected information is used solely for the provision of this Service.

## Article 6 (Handling of Personal Information)

1. The Operator appropriately manages acquired information based on the Privacy Policy.
2. The Operator will not provide personal information to third parties without user consent.

## Article 7 (Protection of Intellectual Property Rights)

1. All content, software, and designs related to this Service are owned by the Operator or its licensors, and users must not use them without permission.
2. If an intellectual property rights infringement is discovered, the Operator reserves the right to take appropriate action.

## Article 8 (Consent Management)

1. Users agree to these Terms and the Privacy Policy when using this Service.
2. The Operator appropriately manages users' consent history, which can be reviewed as necessary.

## Article 9 (Privacy Policy Disclosure)

1. The Operator formulates a detailed policy on the collection, use, sharing, and protection of personal information and discloses it in these Terms or separately.
2. By using this Service, users are deemed to have agreed to the Privacy Policy.

## Article 10 (Modification and Termination of Service)

1. The Operator reserves the right to modify or terminate the content of this Service with prior notice.
2. Notification regarding service modifications or termination will be provided to users through appropriate means.

## Article 11 (Limitation of Liability)

1. The Operator is not responsible for any damages arising from the use of this Service, except as required by applicable law.
2. The Operator is not liable for any losses, damages, or troubles that occur due to the use of this Service.

## Article 12 (Prohibited Activities)

1. Users must not engage in the following activities:
   - Using this Service for commercial purposes.
   - Analyzing, modifying, or reverse engineering the source code of this Service.
   - Unauthorized use of this Service or allowing third parties to use it.
   - Engaging in activities that violate laws or public morals.

## Article 13 (Service Suspension and Account Deletion)

1. If a user violates these Terms, the Operator may suspend the user's access to this Service or delete their account.
2. Upon termination of these Terms, the Operator may delete the user's account.

## Article 14 (Amendment of Terms)

1. The Operator may amend these Terms in accordance with Article 548-4 of the Civil Code.
2. If users continue to use this Service after the amendment, they are deemed to have agreed to the amended Terms.

## Article 15 (Governing Law and Jurisdiction)

1. These Terms shall be governed by the laws of Japan.
2. Any disputes arising from these Terms shall be subject to the exclusive jurisdiction of the Tokyo District Court as the court of first instance.

`;

  return (
    <Box sx={{ height: "100vh" }}>
      <Container maxWidth={isMobile ? "xs" : "md"}>
        {isMobile ? (
          <Box mt={2}>
            <ReactMarkdown>{markdown}</ReactMarkdown>
          </Box>
        ) : (
          <Paper sx={{ p: 3, boxShadow: 1, borderRadius: 3 }}>
            <ReactMarkdown>{markdown}</ReactMarkdown>
          </Paper>
        )}
      </Container>
    </Box>
  );
};

export default Privacy;
