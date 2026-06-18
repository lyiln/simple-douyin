export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T | null;
  requestId?: string | null;
}

export interface UserSummary {
  id: number;
  username: string;
  nickname: string;
  avatarUrl: string | null;
}

export interface AuthData {
  user: UserSummary;
  accessToken: string;
  expiresIn: number;
}

export interface UserProfile {
  id: number;
  username: string;
  nickname: string;
  avatarUrl: string | null;
  videoCount: number;
  likedCount: number;
}

export interface MeData {
  profile: UserProfile;
}

export interface AuthorSummary {
  id: number;
  username: string;
  nickname: string;
  avatarUrl: string | null;
}

export interface ViewerState {
  liked: boolean;
  viewed: boolean;
  owner: boolean;
}

export interface VideoPostResponse {
  id: number;
  author: AuthorSummary;
  caption: string;
  videoUrl: string;
  coverUrl: string | null;
  durationMs: number | null;
  likeCount: number;
  viewCount: number;
  commentCount: number;
  visibility: string;
  status: string;
  createdAt: string;
  viewerState: ViewerState;
}

export interface RecommendedFeedData {
  items: VideoPostResponse[];
  nextCursor: string | null;
  hasMore: boolean;
  strategy?: string | null;
}

export interface ResetRecommendedHistoryData {
  reset: boolean;
  clearedCount: number;
}

export interface MyVideosData {
  items: VideoPostResponse[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface LikeData {
  videoId: number;
  liked: boolean;
  likeCount: number;
}

export interface ViewData {
  videoId: number;
  viewed: boolean;
  viewCount: number;
}

export interface DeleteVideoData {
  videoId: number;
  deleted: boolean;
}

export interface CreateVideoData {
  video: VideoPostResponse;
}

export interface CommentResponse {
  id: number;
  videoId: number;
  author: AuthorSummary;
  content: string;
  createdAt: string;
}

export interface GetCommentsData {
  items: CommentResponse[];
  nextCursor: string | null;
  hasMore: boolean;
  commentCount: number;
}

export interface PostCommentData {
  comment: CommentResponse;
  commentCount: number;
}
