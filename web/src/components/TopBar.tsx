import type { UserProfile, UserSummary } from "../types";
import { IconUpload, IconUser } from "./Icons";
import logoMark from "../assets/douyin/logo-mark.svg";
import avatarFallback from "../assets/douyin/avatar-fallback.svg";
import { resolveAssetUrl } from "../api";

export type RoutePath = "/" | "/me" | "/upload";

interface TopBarProps {
  route: RoutePath;
  user: UserProfile | UserSummary | null;
  onNavigate: (path: RoutePath) => void;
  onAuthClick: () => void;
}

export function TopBar({ route, user, onNavigate, onAuthClick }: TopBarProps) {
  const avatar = resolveAssetUrl(user?.avatarUrl) || avatarFallback;

  return (
    <header className="top-bar">
      <button className="brand-button" type="button" onClick={() => onNavigate("/")} aria-label="回到推荐">
        <img src={logoMark} alt="" />
      </button>
      <nav className="top-actions" aria-label="主导航">
        <button type="button" className={route === "/" ? "active" : ""} onClick={() => onNavigate("/")}>
          推荐
        </button>
        <button type="button" className={route === "/upload" ? "active" : ""} onClick={() => onNavigate("/upload")}>
          <IconUpload />
          投稿
        </button>
        <button type="button" className={route === "/me" ? "active" : ""} onClick={() => onNavigate("/me")}>
          <IconUser />
          我的
        </button>
        <button className="avatar-chip" type="button" onClick={user ? () => onNavigate("/me") : onAuthClick}>
          <img src={avatar} alt={user?.nickname || "登录"} />
        </button>
      </nav>
    </header>
  );
}
