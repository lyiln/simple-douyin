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
                <img src={resolveAssetUrl(video.coverUrl) || defaultCover} alt="" />
                <div className="work-gradient" />
                <button type="button" className="delete-work" onClick={() => void handleDelete(video.id)} aria-label="删除作品">
                  <IconTrash />
                </button>
                <div className="work-copy">
                  <p>{video.caption}</p>
                  <span>
                    {formatDate(video.createdAt)} · {formatCount(video.likeCount)} 赞
                  </span>
                </div>
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
    </main>
  );
}

function mergePosts(current: VideoPostResponse[], incoming: VideoPostResponse[]): VideoPostResponse[] {
  const ids = new Set(current.map((post) => post.id));
  return current.concat(incoming.filter((post) => !ids.has(post.id)));
}
