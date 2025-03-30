import ReactMarkdown from "react-markdown";
import { Box, Container, Paper, useMediaQuery, useTheme } from "@mui/material";

const Privacy = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));
  const markdown = `
# IdP Service Privacy Policy

## Overview
The operator of the IDP Service ("Operator") appropriately handles users' personal information. This Privacy Policy explains how we collect, use, share, and protect personal information.

## Personal Information Collected
We collect the following types of information when users use this service:

### **1. Account Information**
- ID, account name, email address, phone number

### **2. Profile Information**
- User icon, name, gender, date of birth, address

### **3. Device Information**
- IP address, device identifier, browser type, OS information

### **4. Usage Information**
- Service usage history, access logs, API endpoint usage

### **5. Location Information**
- Data obtained using location services as needed

### **6. Cookies and Similar Technologies**
- Data collected using cookies to enhance user experience

## Purpose of Personal Information Use
We use collected personal information primarily for the following purposes:

1. **Authentication and login management**
2. **Service provision and maintenance** (identity verification, transaction verification, account management)
3. **Security enhancement** (preventing unauthorized access, account protection)
4. **User support and troubleshooting**
5. **Service improvement and new feature development**
6. **Advertising delivery and optimization** (tailored ads based on user interests)
7. **Compliance with legal requirements**

## Sharing of Personal Information
We may share collected personal information with third parties only in the following cases:

- When required by law
- When outsourcing necessary tasks for service operation
- When explicit user consent is obtained
- When sharing anonymized data with advertising partners

## Protection Measures for Personal Information
- Encryption and access restrictions to protect personal information
- System monitoring and security measures to prevent unauthorized access
- Regular risk assessments and employee training for better data protection

## Retention Period of Personal Information
- Personal information is retained while the user continues to use the service.
- If a user deletes their account or is inactive for a certain period, the information is deleted according to applicable laws.

## User Rights
Users have the following rights regarding their personal information:

- Access, correction, or deletion of personal information
- Objecting to the processing of personal data
- Requesting data portability

## Use of Cookies and Tracking Technologies
We use cookies and tracking technologies to analyze user behavior. Users can manage cookie settings through their browsers.

## Changes to the Privacy Policy
- This Privacy Policy may be revised as necessary.
- Users will be notified in advance of significant changes, and consent will be obtained as required.

## Contact Information
For inquiries regarding personal information handling, please contact us:

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
