import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { internalIssuer } from "@/app/auth";

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;

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

    // Call IDP server authentication device delete API
    const response = await fetch(
      `${internalIssuer}/v1/me/authentication-devices/${encodeURIComponent(id)}`,
      {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
        },
      }
    );

    if (!response.ok) {
      // Try to parse error response
      try {
        const data = await response.json();
        return NextResponse.json(data, { status: response.status });
      } catch {
        return NextResponse.json(
          {
            error: "server_error",
            error_description: "Failed to delete authentication device",
          },
          { status: response.status }
        );
      }
    }

    // Return success response (204 No Content from server)
    return NextResponse.json(
      { message: "Authentication device deleted successfully" },
      { status: 200 }
    );
  } catch (error) {
    console.error("Authentication device delete error:", error);
    return NextResponse.json(
      {
        error: "server_error",
        error_description: "An internal server error occurred",
      },
      { status: 500 }
    );
  }
}
