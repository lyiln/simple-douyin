import { FormEvent, useMemo, useState } from "react";
import { ApiError, login, register } from "../api";
import type { AuthData } from "../types";
import logoMark from "../assets/douyin/logo-mark.svg";

interface AuthModalProps {
  open: boolean;
  onAuthenticated: (data: AuthData) => void;
  onClose?: () => void;
}

export function AuthModal({ open, onAuthenticated, onClose }: AuthModalProps) {
  const seed = useMemo(() => String(Date.now()).slice(-6), []);
  const [mode, setMode] = useState<"login" | "register">("register");
  const [username, setUsername] = useState(`web_${seed}`);
  const [password, setPassword] = useState("Passw0rd!");
  const [nickname, setNickname] = useState("Web用户");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  if (!open) return null;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const normalizedUsername = username.trim();
    const normalizedNickname = nickname.trim();
    if (!normalizedUsername || !password || (mode === "register" && !normalizedNickname)) {
      setMessage("请完整填写账号信息");
      return;
    }

    setLoading(true);
    setMessage(null);
    try {
      const data =
        mode === "register"
          ? await register(normalizedUsername, password, normalizedNickname)
          : await login(normalizedUsername, password);
      onAuthenticated(data);
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "网络请求失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <form className="auth-panel" onSubmit={handleSubmit}>
        <div className="auth-brand">
          <img src={logoMark} alt="简易抖音" />
          <div>
            <strong>简易抖音</strong>
            <span>登录后进入推荐视频流</span>
          </div>
        </div>
        <div className="auth-tabs" aria-label="账号模式">
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>
            注册
          </button>
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>
            登录
          </button>
        </div>
        <label>
          <span>用户名</span>
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </label>
        {mode === "register" && (
          <label>
            <span>昵称</span>
            <input value={nickname} onChange={(event) => setNickname(event.target.value)} />
          </label>
        )}
        {message && <p className="form-error">{message}</p>}
        <button className="primary-button" type="submit" disabled={loading}>
          {loading ? "处理中..." : mode === "register" ? "注册并进入" : "登录"}
        </button>
        {onClose && (
          <button className="ghost-button" type="button" onClick={onClose}>
            暂不登录
          </button>
        )}
      </form>
    </div>
  );
}
