import type {
  ApiResponse,
  AuthData,
  CreateVideoData,
  DeleteVideoData,
  GetCommentsData,
  LikeData,
  MeData,
  MyVideosData,
  PostCommentData,
  RecommendedFeedData,
  ViewData
} from "./types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");
const TOKEN_KEY = "access_token";

export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    public readonly status?: number,
    public readonly requestId?: string | null
  ) {
    super(message);
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export function isLoggedIn(): boolean {
  return Boolean(getToken());
}

export function resolveAssetUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  if (/^(https?:|blob:|data:|file:)/i.test(value)) return value;
  return `${API_BASE_URL}${value.startsWith("/") ? value : `/${value}`}`;
}

export async function login(username: string, password: string): Promise<AuthData> {
  const data = await apiRequest<AuthData>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  });
  setToken(data.accessToken);
  return data;
}

export async function register(username: string, password: string, nickname: string): Promise<AuthData> {
  const data = await apiRequest<AuthData>("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify({ username, password, nickname })
  });
  setToken(data.accessToken);
  return data;
}

export async function logout(): Promise<void> {
  try {
    await apiRequest<{ loggedOut: boolean }>("/api/v1/auth/logout", { method: "POST" });
  } finally {
    setToken(null);
  }
}

export function getMe(): Promise<MeData> {
  return apiRequest<MeData>("/api/v1/me");
}

export function getRecommendedVideos(cursor: string | null, limit = 10): Promise<RecommendedFeedData> {
  return apiRequest<RecommendedFeedData>(withQuery("/api/v1/feeds/recommended/videos", { cursor, limit }));
}

export function recordView(videoId: number): Promise<ViewData> {
  return apiRequest<ViewData>(`/api/v1/videos/${videoId}/views/me`, {
    method: "POST",
    body: JSON.stringify({ source: "recommended_feed", watchDurationMs: 0 })
  });
}

export function likeVideo(videoId: number): Promise<LikeData> {
  return apiRequest<LikeData>(`/api/v1/videos/${videoId}/likes/me`, { method: "PUT" });
}

export function unlikeVideo(videoId: number): Promise<LikeData> {
  return apiRequest<LikeData>(`/api/v1/videos/${videoId}/likes/me`, { method: "DELETE" });
}

export function getComments(videoId: number, cursor: string | null = null, limit = 20): Promise<GetCommentsData> {
  return apiRequest<GetCommentsData>(withQuery(`/api/v1/videos/${videoId}/comments`, { cursor, limit }));
}

export function postComment(videoId: number, content: string): Promise<PostCommentData> {
  return apiRequest<PostCommentData>(`/api/v1/videos/${videoId}/comments`, {
    method: "POST",
    body: JSON.stringify({ content })
  });
}

export function getMyVideos(cursor: string | null, limit = 18): Promise<MyVideosData> {
  return apiRequest<MyVideosData>(withQuery("/api/v1/me/videos", { cursor, limit }));
}

export function deleteVideo(videoId: number): Promise<DeleteVideoData> {
  return apiRequest<DeleteVideoData>(`/api/v1/videos/${videoId}`, { method: "DELETE" });
}

export function publishVideo(formData: FormData): Promise<CreateVideoData> {
  return apiRequest<CreateVideoData>("/api/v1/videos", {
    method: "POST",
    body: formData
  });
}

async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (!(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json; charset=utf-8");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;

  if (!response.ok || !payload || payload.code !== 0 || payload.data == null) {
    const code = payload?.code ?? response.status;
    const message = payload?.message || response.statusText || "request failed";
    throw new ApiError(code, message, response.status, payload?.requestId);
  }

  return payload.data;
}

function withQuery(path: string, params: Record<string, string | number | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== "") search.set(key, String(value));
  });
  const query = search.toString();
  return query ? `${path}?${query}` : path;
}
