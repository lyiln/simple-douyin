import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, getRecommendedVideos, isLoggedIn, likeVideo, recordView, unlikeVideo } from "../api";
import { CommentDrawer } from "../components/CommentDrawer";
import { IconChevronDown, IconChevronUp } from "../components/Icons";
import { VideoCard } from "../components/VideoCard";
import type { VideoPostResponse } from "../types";

interface RecommendPageProps {
  authVersion: number;
  onRequireAuth: () => void;
}

export function RecommendPage({ authVersion, onRequireAuth }: RecommendPageProps) {
  const [posts, setPosts] = useState<VideoPostResponse[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [drawerPost, setDrawerPost] = useState<VideoPostResponse | null>(null);
  const viewedIds = useRef<Set<number>>(new Set());
  const wheelLock = useRef(0);

  const activePost = posts[activeIndex] ?? null;
  const canGoPrev = activeIndex > 0;
  const canGoNext = activeIndex < posts.length - 1 || hasMore;

  const loadFeed = useCallback(
    async (reset = false) => {
      if (!isLoggedIn()) {
        onRequireAuth();
        return;
      }
      if (loading) return;
      setLoading(true);
      setError(null);
      try {
        const data = await getRecommendedVideos(reset ? null : nextCursor, 10);
        setPosts((current) => (reset ? data.items : mergePosts(current, data.items)));
        setNextCursor(data.nextCursor);
        setHasMore(data.hasMore);
        if (reset) {
          setActiveIndex(0);
          viewedIds.current.clear();
        }
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) onRequireAuth();
        setError(err instanceof Error ? err.message : "推荐视频加载失败");
      } finally {
        setLoading(false);
      }
    },
    [loading, nextCursor, onRequireAuth]
  );

  useEffect(() => {
    if (!isLoggedIn()) {
      setPosts([]);
      setActiveIndex(0);
      onRequireAuth();
      return;
    }
    void loadFeed(true);
  }, [authVersion]);

  useEffect(() => {
    if (!activePost || viewedIds.current.has(activePost.id)) return;
    viewedIds.current.add(activePost.id);
    void recordView(activePost.id)
      .then((data) => {
        setPosts((current) =>
          current.map((post) =>
            post.id === data.videoId
              ? { ...post, viewCount: data.viewCount, viewerState: { ...post.viewerState, viewed: data.viewed } }
              : post
          )
        );
      })
      .catch(() => undefined);
  }, [activePost?.id]);

  useEffect(() => {
    if (activeIndex >= posts.length - 2 && hasMore && !loading) void loadFeed(false);
  }, [activeIndex, posts.length, hasMore, loading, loadFeed]);

  const statusText = useMemo(() => {
    if (loading && posts.length === 0) return "加载推荐视频中...";
    if (!loading && posts.length === 0) return "暂无可推荐视频";
    return null;
  }, [loading, posts.length]);

  function move(delta: number) {
    setActiveIndex((value) => {
      const next = value + delta;
      if (next < 0) return 0;
      if (next >= posts.length) return posts.length - 1;
      return next;
    });
  }

  function handleWheel(event: React.WheelEvent<HTMLElement>) {
    if (Math.abs(event.deltaY) < 40) return;
    const now = Date.now();
    if (now - wheelLock.current < 520) return;
    wheelLock.current = now;
    move(event.deltaY > 0 ? 1 : -1);
  }

  async function handleLike(post: VideoPostResponse) {
    if (!isLoggedIn()) {
      onRequireAuth();
      return;
    }
    const previous = post.viewerState.liked;
    setPosts((current) =>
      current.map((item) =>
        item.id === post.id
          ? {
              ...item,
              likeCount: Math.max(0, item.likeCount + (previous ? -1 : 1)),
              viewerState: { ...item.viewerState, liked: !previous }
            }
          : item
      )
    );
    try {
      const data = previous ? await unlikeVideo(post.id) : await likeVideo(post.id);
      setPosts((current) =>
        current.map((item) =>
          item.id === post.id
            ? { ...item, likeCount: data.likeCount, viewerState: { ...item.viewerState, liked: data.liked } }
            : item
        )
      );
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onRequireAuth();
      setPosts((current) =>
        current.map((item) =>
          item.id === post.id
            ? { ...item, likeCount: post.likeCount, viewerState: { ...item.viewerState, liked: previous } }
            : item
        )
      );
    }
  }

  const updateCommentCount = useCallback((videoId: number, count: number) => {
    setPosts((current) => current.map((post) => (post.id === videoId ? { ...post, commentCount: count } : post)));
    setDrawerPost((current) =>
      current?.id === videoId && current.commentCount !== count ? { ...current, commentCount: count } : current
    );
  }, []);

  return (
    <main className="recommend-page" onWheel={handleWheel}>
      <section className="feed-stage" aria-label="推荐视频流">
        {statusText && <div className="feed-state">{statusText}</div>}
        {error && (
          <div className="feed-state error-state">
            <p>{error}</p>
            <button type="button" onClick={() => void loadFeed(true)}>
              重试
            </button>
          </div>
        )}
        {posts.map((post, index) => (
          <div className={index === activeIndex ? "feed-item active" : "feed-item"} key={post.id}>
            <VideoCard
              post={post}
              active={index === activeIndex}
              onLike={() => void handleLike(post)}
              onComment={() => setDrawerPost(post)}
            />
          </div>
        ))}
      </section>
      <div className="feed-switcher" aria-label="视频切换">
        <button type="button" onClick={() => move(-1)} disabled={!canGoPrev} aria-label="上一个视频">
          <IconChevronUp />
        </button>
        <button type="button" onClick={() => move(1)} disabled={!canGoNext} aria-label="下一个视频">
          <IconChevronDown />
        </button>
      </div>
      <CommentDrawer
        post={drawerPost}
        open={Boolean(drawerPost)}
        onClose={() => setDrawerPost(null)}
        onCommentCountChange={updateCommentCount}
        onRequireAuth={onRequireAuth}
      />
    </main>
  );
}

function mergePosts(current: VideoPostResponse[], incoming: VideoPostResponse[]): VideoPostResponse[] {
  const ids = new Set(current.map((post) => post.id));
  return current.concat(incoming.filter((post) => !ids.has(post.id)));
}
