/**
 * The one place that talks to the BookHive backend (/api/v1).
 *
 *   const book = await api.get<Book>(`/books/${id}`);
 *   const page = await api.get<PageResponse<Book>>("/books", { query: { page: 0, size: 20 } });
 *   await api.post("/auth/login", { email, password });
 *
 * Every non-2xx response is thrown as an {@link ApiError} built from the backend's
 * RFC 7807 ProblemDetail, so pages can switch on `error.code` (e.g. "LOAN_LIMIT_REACHED").
 */

import type { components } from "@/lib/api-schema";

// On the server (SSR) the backend may have an internal address (e.g. http://backend:8080 in
// Docker); in the browser it must be the public one. NEXT_PUBLIC_* is baked in at build time.
export const API_BASE_URL = (
  (typeof window === "undefined" ? process.env.API_URL : undefined) ??
  process.env.NEXT_PUBLIC_API_URL ??
  "http://localhost:8080/api/v1"
).replace(/\/$/, "");

const DEFAULT_TIMEOUT_MS = 15_000;

/**
 * A backend DTO type, generated from the OpenAPI spec (pnpm api:types), e.g.
 * `type Book = Schema<"BookResponse">`. Never write these types by hand.
 */
export type Schema<Name extends keyof components["schemas"]> = components["schemas"][Name];

/** Same shape as the backend's PageResponse. `page` starts at 0. */
export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/** One invalid field, from the backend's VALIDATION_FAILED errors list. */
export type ValidationError = { field: string; code: string; message: string };

/** The backend's error body (RFC 7807 ProblemDetail + our extra fields). */
type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: ValidationError[];
};

export class ApiError extends Error {
  /** HTTP status; 0 when the server couldn't be reached at all. */
  readonly status: number;
  /** Stable machine-readable code, e.g. BOOK_NOT_FOUND. Translate this, not `message`. */
  readonly code: string;
  readonly errors: ValidationError[];
  /** X-Request-Id: lets us find this exact request in the backend logs. */
  readonly requestId?: string;

  constructor(options: {
    status: number;
    code: string;
    message: string;
    errors?: ValidationError[];
    requestId?: string;
  }) {
    super(options.message);
    this.name = "ApiError";
    this.status = options.status;
    this.code = options.code;
    this.errors = options.errors ?? [];
    this.requestId = options.requestId;
  }

  get isValidation() {
    return this.code === "VALIDATION_FAILED";
  }

  /** { email: "must be a well-formed email address", ... } for showing under form fields. */
  fieldErrors(): Record<string, string> {
    return Object.fromEntries(this.errors.map((e) => [e.field, e.message]));
  }
}

// --- Access token -------------------------------------------------------------------------
// The login token lives in memory, not localStorage (safer against XSS). The auth feature
// registers how to read it (checklist step 107); until then requests are anonymous.

type AccessTokenProvider = () => string | null | undefined;
let accessTokenProvider: AccessTokenProvider = () => null;

export function setAccessTokenProvider(provider: AccessTokenProvider) {
  accessTokenProvider = provider;
}

/**
 * Called when a request with a token gets 401 (the 15-minute access token expired). Should get a
 * new token and return true if that worked; the request is then retried once (step 113).
 */
type UnauthorizedHandler = () => Promise<boolean>;
let unauthorizedHandler: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

// --- Requests -----------------------------------------------------------------------------

type QueryValue = string | number | boolean | null | undefined;
export type Query = Record<string, QueryValue | QueryValue[]>;

export type RequestOptions = Omit<RequestInit, "method" | "body"> & {
  /** Added to the URL; null/undefined values are skipped, arrays repeat the key. */
  query?: Query;
  timeoutMs?: number;
  /** "blob" for files (images, PDFs) instead of JSON. */
  responseType?: "json" | "blob";
  /** File uploads: called with 0..1 while the body is sent (uses XMLHttpRequest). */
  onUploadProgress?: (fraction: number) => void;
};

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

function buildUrl(path: string, query?: Query): string {
  if (!path.startsWith("/")) {
    throw new Error(`API path must start with "/": ${path}`);
  }
  const url = new URL(API_BASE_URL + path);
  for (const [key, value] of Object.entries(query ?? {})) {
    for (const item of Array.isArray(value) ? value : [value]) {
      if (item !== null && item !== undefined) {
        url.searchParams.append(key, String(item));
      }
    }
  }
  return url.toString();
}

/**
 * One request, plus one silent retry when the access token had expired: the user never sees the
 * 401. Not for /auth/* itself (a failed login or refresh is a real answer) and only once, so a
 * truly logged-out user can't cause a loop.
 */
async function request<T>(
  method: Method,
  path: string,
  body: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const sentToken = !!accessTokenProvider();
  try {
    return await send<T>(method, path, body, options);
  } catch (error) {
    const expiredToken =
      error instanceof ApiError && error.status === 401 && sentToken && !path.startsWith("/auth/");
    if (expiredToken && unauthorizedHandler && (await unauthorizedHandler())) {
      return send<T>(method, path, body, options);
    }
    throw error;
  }
}

async function send<T>(
  method: Method,
  path: string,
  body: unknown,
  {
    query,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    headers,
    signal,
    responseType = "json",
    onUploadProgress,
    ...init
  }: RequestOptions = {},
): Promise<T> {
  const finalHeaders = new Headers(headers);
  finalHeaders.set("Accept", "application/json, application/problem+json");

  // FormData (file uploads) sets its own Content-Type with the multipart boundary
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
  if (body !== undefined && !isFormData) {
    finalHeaders.set("Content-Type", "application/json");
  }

  const token = accessTokenProvider();
  if (token) {
    finalHeaders.set("Authorization", `Bearer ${token}`);
  }

  // Outside the try: a programming error (bad path) must not be reported as "network error"
  const url = buildUrl(path, query);

  let response: Response;
  try {
    response =
      onUploadProgress && isFormData
        ? await sendWithProgress(method, url, finalHeaders, body, timeoutMs, onUploadProgress)
        : await fetch(url, {
            ...init,
            method,
            headers: finalHeaders,
            body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
            // Send/receive the httpOnly refresh-token cookie (backend CORS allows credentials)
            credentials: "include",
            signal: signal ?? AbortSignal.timeout(timeoutMs),
          });
  } catch (cause) {
    // Check the name, not `instanceof DOMException`: the class differs between environments
    const timedOut = (cause as { name?: string } | null)?.name === "TimeoutError";
    throw new ApiError({
      status: 0,
      code: timedOut ? "TIMEOUT" : "NETWORK_ERROR",
      message: timedOut
        ? "The server took too long to respond. Please try again."
        : "Can't reach the server. Check your connection and try again.",
    });
  }

  const requestId = response.headers.get("X-Request-Id") ?? undefined;

  if (!response.ok) {
    throw await toApiError(response, requestId);
  }

  if (responseType === "blob") {
    return (await response.blob()) as T;
  }
  // 204 No Content, or an empty body
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * Like fetch, but reports upload progress (fetch can't). Resolves to a normal Response so the
 * rest of send() treats both the same. Errors are thrown like fetch's: TimeoutError or TypeError.
 */
function sendWithProgress(
  method: Method,
  url: string,
  headers: Headers,
  body: FormData,
  timeoutMs: number,
  onProgress: (fraction: number) => void,
): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(method, url);
    xhr.withCredentials = true;
    // Uploads of a few MB on a slow mobile connection need more time than normal requests
    xhr.timeout = Math.max(timeoutMs, 120_000);
    headers.forEach((value, key) => xhr.setRequestHeader(key, value));
    xhr.responseType = "blob";
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(event.loaded / event.total);
    };
    xhr.onload = () => {
      onProgress(1);
      const responseHeaders = new Headers();
      for (const line of xhr
        .getAllResponseHeaders()
        .trim()
        .split(/[\r\n]+/)) {
        const at = line.indexOf(":");
        if (at > 0) responseHeaders.append(line.slice(0, at).trim(), line.slice(at + 1).trim());
      }
      // A Response can't have a body with 204
      const noBody = xhr.status === 204 || xhr.status === 205;
      resolve(
        new Response(noBody ? null : (xhr.response as Blob), {
          status: xhr.status,
          statusText: xhr.statusText,
          headers: responseHeaders,
        }),
      );
    };
    xhr.onerror = () => reject(new TypeError("Network request failed"));
    xhr.ontimeout = () => reject(Object.assign(new Error("Timed out"), { name: "TimeoutError" }));
    xhr.send(body);
  });
}

async function toApiError(response: Response, requestId?: string): Promise<ApiError> {
  let problem: ProblemDetail = {};
  const contentType = response.headers.get("Content-Type") ?? "";
  if (contentType.includes("json")) {
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Body wasn't valid JSON; fall back to the status below
    }
  }
  return new ApiError({
    status: response.status,
    code: problem.code ?? `HTTP_${response.status}`,
    message: problem.detail ?? problem.title ?? response.statusText ?? "Request failed",
    errors: problem.errors,
    requestId,
  });
}

export const api = {
  get: <T>(path: string, options?: RequestOptions) => request<T>("GET", path, undefined, options),
  /** A file (image, PDF) as a Blob, sent with the access token like any other request. */
  getBlob: (path: string, options?: RequestOptions) =>
    request<Blob>("GET", path, undefined, { ...options, responseType: "blob" }),
  post: <T = void>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>("POST", path, body, options),
  put: <T = void>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>("PUT", path, body, options),
  patch: <T = void>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>("PATCH", path, body, options),
  delete: <T = void>(path: string, options?: RequestOptions) =>
    request<T>("DELETE", path, undefined, options),
};
