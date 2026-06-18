import { useCallback, useEffect, useState } from "react";
import { ApiError, deleteVideo, getMe, getMyVideos, isLoggedIn, resolveAssetUrl } from "../api";
import { IconTrash } from "../components/Icons";
import type { UserProfile, VideoPostResponse } from "../types";
import { formatCount, formatDate } from "../utils";
import avatarFallback from "../assets/douyin/avatar-fallback.svg";
import defaultCover from "../assets/douyin/default-cover.svg";
import emptyBox from "../assets/douyin/empty-box.svg";

interface ProfilePageProps {
  authVersion: number;
  onRequireAuth: () => void;
  onLogout: () => void;
}

export function ProfilePage({ authVersion, onRequireAuth, onLogout }: ProfilePageProps) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [videos, setVideos] = useState<VideoPostResponse[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedVideo, setSelectedVideo] = useState<VideoPostResponse | null>(null);

  const loadProfile = useCallback(
    async (reset = false) => {
      if (!isLoggedIn()) {
        onRequireAuth();
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const [me, myVideos] = await Promise.all([getMe(), getMyVideos(reset ? null : nextCursor, 18)]);
        setProfile(me.profile);
        setVideos((current) => (reset ? myVideos.items : mergePosts(current, myVideos.items)));
        setNextCursor(myVideos.nextCursor);
        setHasMore(myVideos.hasMore);
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) onRequireAuth();
        setError(err instanceof Error ? err.message : "个人主页加载失败");
      } finally {
        setLoading(false);
      }
    },
    [nextCursor, onRequireAuth]
  );

  useEffect(() => {
    if (!isLoggedIn()) {
      setProfile(null);
      setVideos([]);
      onRequireAuth();
      return;
    }
    void loadProfile(true);
  }, [authVersion]);

  async function handleDelete(videoId: number) {
    if (!window.confirm("确定删除这个作品吗？")) return;
    try {
      await deleteVideo(videoId);
      setVideos((current) => current.filter((video) => video.id !== videoId));
      setSelectedVideo((current) => (current?.id === videoId ? null : current));
      setProfile((current) =>
        current ? { ...current, videoCount: Math.max(0, current.videoCount - 1) } : current
      );
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onRequireAuth();
      setError(err instanceof Error ? err.message : "删除失败");
    }
  }

  const avatar = resolveAssetUrl(profile?.avatarUrl) || avatarFallback;

  return (
    <main className="profile-page">
      <section className="profile-hero">
        <div className="profile-cover" />
        <div className="profile-info">
          <img className="profile-avatar" src={avatar} alt={profile?.nickname || "头像"} />
          <div className="profile-copy">
            <h1>{profile?.nickname || "未登录用户"}</h1>
            <p>抖音号：{profile?.username || "登录后查看"}</p>
            <div className="profile-stats" aria-label="个人统计">
              <span>
                <strong>{formatCount(profile?.videoCount || videos.length)}</strong>作品
              </span>
              <span>
                <strong>{formatCount(profile?.likedCount || 0)}</strong>获赞
              </span>
            </div>
          </div>
          <button className="logout-button" type="button" onClick={onLogout}>
            退出登录
          </button>
        </div>
      </section>

      <section className="works-section">
        <div className="works-tabs">
          <button className="active" type="button">
            作品
          </button>
        </div>
        {error && <p className="page-error">{error}</p>}
        {!loading && videos.length === 0 ? (
          <div className="empty-state">
            <img src={emptyBox} alt="" />
            <strong>还没有发布作品</strong>
            <span>发布后会出现在这里</span>
          </div>
        ) : (
          <div className="works-grid">
            {videos.map((video) => (
              <article className="work-card" key={video.id}>
                <button
                  type="button"
                  className="work-open"
                  onClick={() => setSelectedVideo(video)}
                  aria-label={`查看作品：${video.caption || "未命名视频"}`}
                >
                  <img src={resolveAssetUrl(video.coverUrl) || defaultCover} alt="" />
                  <span className="work-gradient" />
                  <span className="work-copy">
                    <span className="work-caption">{video.caption}</span>
                    <span className="work-meta">
                      {formatDate(video.createdAt)} · {formatCount(video.likeCount)} 赞
                    </span>
                  </span>
                </button>
                <button type="button" className="delete-work" onClick={() => void handleDelete(video.id)} aria-label="删除作品">
                  <IconTrash />
                </button>
              </article>
            ))}
          </div>
        )}
        {loading && <p className="muted-center">加载中...</p>}
        {hasMore && videos.length > 0 && (
          <button className="load-more" type="button" onClick={() => void loadProfile(false)} disabled={loading}>
            加载更多
          </button>
        )}
      </section>
      {selectedVideo && <ProfileVideoPreview video={selectedVideo} onClose={() => setSelectedVideo(null)} />}
    </main>
  );
}

interface ProfileVideoPreviewProps {
  video: VideoPostResponse;
  onClose: () => void;
}

function ProfileVideoPreview({ video, onClose }: ProfileVideoPreviewProps) {
  const videoUrl = resolveAssetUrl(video.videoUrl);
  const coverUrl = resolveAssetUrl(video.coverUrl) || defaultCover;
  const avatarUrl = resolveAssetUrl(video.author.avatarUrl) || avatarFallback;

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  return (
    <div className="profile-viewer" role="dialog" aria-modal="true" aria-label="查看作品视频">
      <button className="profile-viewer-scrim" type="button" onClick={onClose} aria-label="关闭预览" />
      <section className="profile-viewer-panel">
        <div className="profile-viewer-media">
          {videoUrl ? (
            <video
              className="profile-viewer-backdrop-media"
              src={videoUrl}
              poster={coverUrl}
              muted
              loop
              autoPlay
              playsInline
              preload="metadata"
              aria-hidden="true"
            />
          ) : (
            <img className="profile-viewer-backdrop-media" src={coverUrl} alt="" />
          )}
          {videoUrl ? (
            <video
              className="profile-viewer-video"
              src={videoUrl}
              poster={coverUrl}
              controls
              autoPlay
              muted
              playsInline
              preload="auto"
            />
          ) : (
            <img className="profile-viewer-video profile-viewer-fallback" src={coverUrl} alt="" />
          )}
        </div>
        <aside className="profile-viewer-side">
          <div className="profile-viewer-author">
            <img src={avatarUrl} alt={video.author.nickname || video.author.username} />
            <div>
              <strong>@{video.author.nickname || video.author.username}</strong>
              <span>{formatDate(video.createdAt)}</span>
            </div>
          </div>
          <p>{video.caption}</p>
          <div className="profile-viewer-stats" aria-label="作品数据">
            <span>
              <strong>{formatCount(video.likeCount)}</strong>获赞
            </span>
            <span>
              <strong>{formatCount(video.commentCount)}</strong>评论
            </span>
            <span>
              <strong>{formatCount(video.viewCount)}</strong>播放
            </span>
          </div>
        </aside>
        <button className="profile-viewer-close" type="button" onClick={onClose} autoFocus aria-label="关闭预览">
          <span />
          <span />
        </button>
      </section>
    </div>
  );
}

function mergePosts(current: VideoPostResponse[], incoming: VideoPostResponse[]): VideoPostResponse[] {
  const ids = new Set(current.map((post) => post.id));
  return current.concat(incoming.filter((post) => !ids.has(post.id)));
}
