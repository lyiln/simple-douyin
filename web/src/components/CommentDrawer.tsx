import { FormEvent, useEffect, useRef, useState } from "react";
import { ApiError, getComments, postComment, resolveAssetUrl } from "../api";
import type { CommentResponse, VideoPostResponse } from "../types";
import { formatDate } from "../utils";
import avatarFallback from "../assets/douyin/avatar-fallback.svg";

interface CommentDrawerProps {
  post: VideoPostResponse | null;
  open: boolean;
  onClose: () => void;
  onCommentCountChange: (videoId: number, count: number) => void;
  onRequireAuth: () => void;
}

export function CommentDrawer({ post, open, onClose, onCommentCountChange, onRequireAuth }: CommentDrawerProps) {
  const [mounted, setMounted] = useState(open);
  const [visible, setVisible] = useState(false);
  const [renderedPost, setRenderedPost] = useState<VideoPostResponse | null>(post);
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [count, setCount] = useState(0);
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const closeTimerRef = useRef<number | null>(null);
  const internalCloseTimerRef = useRef<number | null>(null);
  const openTimerRef = useRef<number | null>(null);
  const lastPostRef = useRef<VideoPostResponse | null>(post);
  const closingRef = useRef(false);

  if (post) lastPostRef.current = post;

  useEffect(() => {
    if (closingRef.current) return;

    if (closeTimerRef.current) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    if (openTimerRef.current) {
      window.clearTimeout(openTimerRef.current);
      openTimerRef.current = null;
    }

    if (open && post) {
      lastPostRef.current = post;
      setRenderedPost(post);
      setMounted(true);
      openTimerRef.current = window.setTimeout(() => {
        setVisible(true);
        openTimerRef.current = null;
      }, 20);
    } else {
      setVisible(false);
      closeTimerRef.current = window.setTimeout(() => {
        setMounted(false);
        setRenderedPost(null);
        lastPostRef.current = null;
        closeTimerRef.current = null;
      }, 280);
    }

    return () => {
      if (closeTimerRef.current) {
        window.clearTimeout(closeTimerRef.current);
        closeTimerRef.current = null;
      }
      if (openTimerRef.current) {
        window.clearTimeout(openTimerRef.current);
        openTimerRef.current = null;
      }
    };
  }, [open, post]);

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
      if (internalCloseTimerRef.current) window.clearTimeout(internalCloseTimerRef.current);
      if (openTimerRef.current) window.clearTimeout(openTimerRef.current);
    };
  }, []);

  useEffect(() => {
    if (!open || !post) return;
    setComments([]);
    setCount(post.commentCount);
    setText("");
    setLoading(true);
    setError(null);
    getComments(post.id)
      .then((data) => {
        setComments(data.items);
        setCount(data.commentCount);
        onCommentCountChange(post.id, data.commentCount);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) onRequireAuth();
        setError(err instanceof Error ? err.message : "评论加载失败");
      })
      .finally(() => setLoading(false));
  }, [open, post, onCommentCountChange, onRequireAuth]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const currentPost = post ?? renderedPost ?? lastPostRef.current;
    if (!currentPost || !text.trim() || sending) return;
    setSending(true);
    setError(null);
    try {
      const data = await postComment(currentPost.id, text.trim());
      setComments((current) => [data.comment, ...current]);
      setCount(data.commentCount);
      setText("");
      onCommentCountChange(currentPost.id, data.commentCount);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onRequireAuth();
      setError(err instanceof Error ? err.message : "评论发送失败");
    } finally {
      setSending(false);
    }
  }

  function requestClose() {
    if (internalCloseTimerRef.current) return;
    closingRef.current = true;
    if (closeTimerRef.current) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
    if (openTimerRef.current) {
      window.clearTimeout(openTimerRef.current);
      openTimerRef.current = null;
    }
    setVisible(false);
    internalCloseTimerRef.current = window.setTimeout(() => {
      setMounted(false);
      setRenderedPost(null);
      lastPostRef.current = null;
      internalCloseTimerRef.current = null;
      closingRef.current = false;
      onClose();
    }, 280);
  }

  const currentPost = post ?? renderedPost ?? lastPostRef.current;

  if (!mounted || !currentPost) return null;

  return (
    <>
      <div className={visible ? "comment-scrim visible" : "comment-scrim"} onClick={requestClose} aria-hidden="true" />
      <aside className={visible ? "comment-drawer open" : "comment-drawer closing"} aria-label="评论" aria-hidden={!visible}>
        <div className="drawer-header">
          <strong>{count} 条评论</strong>
          <button type="button" onClick={requestClose} aria-label="关闭评论">
            关闭
          </button>
        </div>
        <div className="comment-list">
          {loading && <p className="muted-center">加载评论中...</p>}
          {!loading && comments.length === 0 && <p className="muted-center">暂无评论</p>}
          {comments.map((comment) => (
            <article className="comment-item" key={comment.id}>
              <img src={resolveAssetUrl(comment.author.avatarUrl) || avatarFallback} alt="" />
              <div>
                <div className="comment-meta">
                  <strong>{comment.author.nickname || comment.author.username}</strong>
                  <span>{formatDate(comment.createdAt)}</span>
                </div>
                <p>{comment.content}</p>
              </div>
            </article>
          ))}
        </div>
        <form className="comment-form" onSubmit={handleSubmit}>
          <input value={text} onChange={(event) => setText(event.target.value)} placeholder="说点什么..." maxLength={300} />
          <button type="submit" disabled={!text.trim() || sending}>
            {sending ? "发送中" : "发送"}
          </button>
        </form>
        {error && <p className="drawer-error">{error}</p>}
      </aside>
    </>
  );
}
