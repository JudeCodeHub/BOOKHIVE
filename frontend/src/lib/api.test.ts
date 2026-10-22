import { afterEach, describe, expect, it, vi } from "vitest";
import { API_BASE_URL, ApiError, api, setAccessTokenProvider } from "@/lib/api";

/** Replaces fetch with a fake that returns `response` and records the request. */
function mockFetch(response: Response | (() => Promise<Response>)) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() =>
    // A body can only be read once, so each call gets its own copy
    typeof response === "function" ? response() : Promise.resolve(response.clone()),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function problem(status: number, body: object) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/problem+json", "X-Request-Id": "req-123" },
  });
}

afterEach(() => {
  setAccessTokenProvider(() => null);
});

describe("api client", () => {
  it("calls the backend with query params, skipping empty values and repeating arrays", async () => {
    const fetchMock = mockFetch(Response.json({ content: [], page: 0 }));

    const page = await api.get<{ page: number }>("/books", {
      query: { q: "harry", page: 0, lang: ["en", "si"], skip: undefined },
    });

    expect(page.page).toBe(0);
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/books?q=harry&page=0&lang=en&lang=si`);
  });

  it("sends the access token and cookies once logged in", async () => {
    const fetchMock = mockFetch(Response.json({}));

    await api.get("/me");
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has("Authorization")).toBe(false);

    setAccessTokenProvider(() => "token-abc");
    await api.get("/me");
    const init = fetchMock.mock.calls[1][1];
    expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer token-abc");
    expect(init?.credentials).toBe("include");
  });

  it("sends a JSON body on POST", async () => {
    const fetchMock = mockFetch(new Response(null, { status: 204 }));

    const result = await api.post("/auth/login", { email: "a@b.lk" });

    const init = fetchMock.mock.calls[0][1];
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe('{"email":"a@b.lk"}');
    expect(new Headers(init?.headers).get("Content-Type")).toBe("application/json");
    expect(result).toBeUndefined(); // 204 No Content
  });

  it("turns a ProblemDetail response into an ApiError with code and request id", async () => {
    mockFetch(problem(404, { detail: "Book 42 was not found", code: "BOOK_NOT_FOUND" }));

    const error = await api.get("/books/42").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 404,
      code: "BOOK_NOT_FOUND",
      message: "Book 42 was not found",
      requestId: "req-123",
    });
  });

  it("exposes validation errors per field", async () => {
    mockFetch(
      problem(400, {
        code: "VALIDATION_FAILED",
        errors: [{ field: "email", code: "Email", message: "must be a well-formed email address" }],
      }),
    );

    const error = (await api.post("/auth/register", {}).catch((e: unknown) => e)) as ApiError;

    expect(error.isValidation).toBe(true);
    expect(error.fieldErrors()).toEqual({ email: "must be a well-formed email address" });
  });

  it("uses HTTP_<status> as the code when the error has no JSON body", async () => {
    mockFetch(new Response(null, { status: 401 }));

    await expect(api.get("/me")).rejects.toMatchObject({ status: 401, code: "HTTP_401" });
  });

  it("reports an unreachable server as NETWORK_ERROR", async () => {
    mockFetch(() => Promise.reject(new TypeError("fetch failed")));

    await expect(api.get("/books")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });

  it("reports a slow server as TIMEOUT", async () => {
    vi.stubGlobal(
      "fetch",
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener("abort", () => reject(init.signal?.reason));
        }),
    );

    await expect(api.get("/slow", { timeoutMs: 10 })).rejects.toMatchObject({ code: "TIMEOUT" });
  });

  it("rejects paths without a leading slash as a programming error, not a network error", async () => {
    mockFetch(Response.json({}));

    await expect(api.get("books")).rejects.toThrow(/must start with/);
  });
});
