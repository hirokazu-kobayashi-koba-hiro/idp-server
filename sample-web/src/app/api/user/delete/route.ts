import { NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { internalIssuer } from "@/app/auth";

export async function DELETE() {
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

    // Call IDP server user delete API
    const response = await fetch(`${internalIssuer}/v1/me`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
      },
    });

    if (!response.ok) {
      const data = await response.json();
      return NextResponse.json(data, { status: response.status });
    }

    // Return 204 No Content on success
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    console.error("User delete error:", error);
    return NextResponse.json(
      {
        error: "server_error",
        error_description: "An internal server error occurred",
      },
      { status: 500 }
    );
  }
}
