import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { internalIssuer } from "@/app/auth";

export async function POST(request: NextRequest) {
  try {
    // Get session
    const session = await auth();

    if (!session || !session.accessToken) {
      return NextResponse.json(
        {
          error: "invalid_token",
          error_description: "The access token is invalid or expired",
        },
        { status: 401 }
      );
    }

    // Parse request body
    const body = await request.json();
    const { current_password, new_password } = body;

    // Validation
    if (!current_password) {
      return NextResponse.json(
        {
          error: "invalid_request",
          error_description: "Current password is required.",
        },
        { status: 400 }
      );
    }

    if (!new_password) {
      return NextResponse.json(
        {
          error: "invalid_request",
          error_description: "New password is required.",
        },
        { status: 400 }
      );
    }

    // Call IDP server password change API
    const response = await fetch(
      `${internalIssuer}/v1/me/password/change`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
        },
        body: JSON.stringify({
          current_password,
          new_password,
        }),
      }
    );

    const data = await response.json();

    if (!response.ok) {
      return NextResponse.json(data, { status: response.status });
    }

    return NextResponse.json(data);
  } catch (error) {
    console.error("Password change error:", error);
    return NextResponse.json(
      {
        error: "server_error",
        error_description: "An internal server error occurred",
      },
      { status: 500 }
    );
  }
}
