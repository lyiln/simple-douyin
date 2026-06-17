import { ChangeEvent, DragEvent, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, isLoggedIn, publishVideo } from "../api";
import { IconUpload } from "../components/Icons";
import type { VideoPostResponse } from "../types";
import uploadCloud from "../assets/douyin/upload-cloud.svg";

interface UploadPageProps {
  onRequireAuth: () => void;
  onPublished: (video: VideoPostResponse) => void;
}

export function UploadPage({ onRequireAuth, onPublished }: UploadPageProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [caption, setCaption] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const previewUrl = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);

  useEffect(() => {
    if (!isLoggedIn()) onRequireAuth();
  }, [onRequireAuth]);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  function pickFile(selected: File | null) {
    setError(null);
    setMessage(null);
    if (!selected) return;
    if (!selected.type.startsWith("video/")) {
      setError("请选择视频文件，推荐 MP4 格式");
      return;
    }
    setFile(selected);
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    pickFile(event.target.files?.[0] || null);
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragOver(false);
    pickFile(event.dataTransfer.files?.[0] || null);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!isLoggedIn()) {
      onRequireAuth();
      return;
    }
    const normalizedCaption = caption.trim();
    if (!file) {
      setError("请先选择视频文件");
      return;
    }
    if (normalizedCaption.length < 1 || normalizedCaption.length > 200) {
      setError("标题需为 1-200 字");
      return;
    }

    setPublishing(true);
    setError(null);
    setMessage(null);
    try {
      const formData = new FormData();
      formData.append("caption", normalizedCaption);
      formData.append("videoFile", file);
      formData.append("visibility", "public");
      const data = await publishVideo(formData);
      setMessage("发布成功");
      setCaption("");
      setFile(null);
      onPublished(data.video);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) onRequireAuth();
      setError(err instanceof Error ? err.message : "发布失败");
    } finally {
      setPublishing(false);
    }
  }

  return (
    <main className="upload-page">
      <section className="upload-shell">
        <div className="upload-heading">
          <span>创作者中心</span>
          <h1>发布视频</h1>
        </div>
        <div className="rule-row">
          <div>
            <strong>视频文件</strong>
            <span>推荐 MP4，内容发布后进入推荐候选池。</span>
          </div>
          <div>
            <strong>标题文案</strong>
            <span>1-200 字，展示在沉浸式视频底部。</span>
          </div>
          <div>
            <strong>课程范围</strong>
            <span>仅保留视频投稿，不提供图文、活动、高清发布。</span>
          </div>
        </div>

        <form className="upload-form" onSubmit={handleSubmit}>
          <div
            className={dragOver ? "drop-zone drag-over" : "drop-zone"}
            onDragOver={(event) => {
              event.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
          >
            {previewUrl ? (
              <video src={previewUrl} controls muted playsInline />
            ) : (
              <div className="drop-empty">
                <img src={uploadCloud} alt="" />
                <strong>点击上传或拖拽视频到这里</strong>
                <span>支持常见视频格式，推荐使用 MP4。</span>
              </div>
            )}
            <input ref={fileInputRef} type="file" accept="video/*" onChange={handleFileChange} hidden />
          </div>

          <label className="caption-field">
            <span>视频标题</span>
            <textarea
              value={caption}
              onChange={(event) => setCaption(event.target.value.slice(0, 200))}
              placeholder="添加作品描述..."
              maxLength={200}
            />
            <small>{caption.trim().length}/200</small>
          </label>

          {error && <p className="page-error">{error}</p>}
          {message && <p className="page-success">{message}</p>}
          <button className="publish-button" type="submit" disabled={publishing}>
            <IconUpload />
            {publishing ? "发布中..." : "发布"}
          </button>
        </form>
      </section>
    </main>
  );
}
