import { useEffect, useMemo, useRef, useState } from "react";
import { resolveAssetUrl } from "../api";
import type { VideoPostResponse } from "../types";
import { clampCaption, formatCount, formatDate } from "../utils";
import { IconComment, IconHeart, IconPlay } from "./Icons";
import avatarFallback from "../assets/douyin/avatar-fallback.svg";
import defaultCover from "../assets/douyin/default-cover.svg";

interface VideoCardProps {
  post: VideoPostResponse;
  active: boolean;
  onLike: () => void;
  onComment: () => void;
}

export function VideoCard({ post, active, onLike, onComment }: VideoCardProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const backdropVideoRef = useRef<HTMLVideoElement | null>(null);
  const [paused, setPaused] = useState(false);
  const videoUrl = resolveAssetUrl(post.videoUrl);
  const coverUrl = resolveAssetUrl(post.coverUrl) || defaultCover;
  const avatarUrl = resolveAssetUrl(post.author.avatarUrl) || avatarFallback;
  const created = useMemo(() => formatDate(post.createdAt), [post.createdAt]);

  useEffect(() => {
    const video = videoRef.current;
    const backdrop = backdropVideoRef.current;
    if (active && !paused) {
      if (backdrop) void backdrop.play().catch(() => undefined);
      if (video) void video.play().catch(() => undefined);
    } else {
      backdrop?.pause();
      video?.pause();
    }
  }, [active, paused, post.id]);

  useEffect(() => {
    setPaused(false);
  }, [post.id]);

  return (
    <section className="video-card" aria-label={post.caption}>
      <div className="video-media" onClick={() => setPaused((value) => !value)}>
        <div className="video-backdrop" aria-hidden="true">
          {videoUrl ? (
            <video
              ref={backdropVideoRef}
              src={videoUrl}
              poster={coverUrl}
              muted
              loop
              playsInline
              preload="metadata"
              tabIndex={-1}
              className="video-backdrop-element"
            />
          ) : (
            <img className="video-backdrop-element" src={coverUrl} alt="" />
          )}
        </div>
        {videoUrl ? (
          <video
            ref={videoRef}
            src={videoUrl}
            poster={coverUrl}
            muted
            loop
            playsInline
            preload={active ? "auto" : "metadata"}
            className="video-element"
          />
        ) : (
          <img className="video-element video-fallback" src={coverUrl} alt="" />
        )}
        {paused && (
          <div className="pause-indicator">
            <IconPlay />
          </div>
        )}
      </div>
      <div className="video-vignette" />
      <div className="video-copy">
        <a className="author-line" href="#" onClick={(event) => event.preventDefault()}>
          @{post.author.nickname || post.author.username}
        </a>
        <p>{clampCaption(post.caption)}</p>
        <div className="meta-line">
          <span>{created}</span>
          <span>简易抖音</span>
        </div>
      </div>
      <div className="action-rail">
        <img className="rail-avatar" src={avatarUrl} alt={post.author.nickname} />
        <button className={post.viewerState.liked ? "rail-button liked" : "rail-button"} type="button" onClick={onLike}>
          <IconHeart />
          <span>{formatCount(post.likeCount)}</span>
        </button>
        <button className="rail-button" type="button" onClick={onComment}>
          <IconComment />
          <span>{formatCount(post.commentCount)}</span>
        </button>
      </div>
    </section>
  );
}
