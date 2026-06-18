import { useCallback, useEffect, useMemo, useState } from "react";
import { getMe, isLoggedIn, logout } from "./api";
import { AuthModal } from "./components/AuthModal";
import { TopBar, type RoutePath } from "./components/TopBar";
import { ProfilePage } from "./pages/ProfilePage";
import { RecommendPage } from "./pages/RecommendPage";
import { UploadPage } from "./pages/UploadPage";
import type { AuthData, UserProfile, VideoPostResponse } from "./types";

function normalizePath(pathname: string): RoutePath {
  if (pathname === "/me") return "/me";
  if (pathname === "/upload") return "/upload";
  return "/";
}

export default function App() {
  const [route, setRoute] = useState<RoutePath>(() => normalizePath(window.location.pathname));
  const [authOpen, setAuthOpen] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);
  const [user, setUser] = useState<UserProfile | null>(null);

  const appClassName = useMemo(() => `app-root route-${route === "/" ? "recommend" : route.slice(1)}`, [route]);
  const openAuth = useCallback(() => setAuthOpen(true), []);

  const refreshMe = useCallback(async () => {
    if (!isLoggedIn()) {
      setUser(null);
      return;
    }
    try {
      const data = await getMe();
      setUser(data.profile);
    } catch {
      setUser(null);
      setAuthOpen(true);
    }
  }, []);

  useEffect(() => {
    void refreshMe();
  }, [refreshMe, authVersion]);

  useEffect(() => {
    const listener = () => setRoute(normalizePath(window.location.pathname));
    window.addEventListener("popstate", listener);
    return () => window.removeEventListener("popstate", listener);
  }, []);

  function navigate(path: RoutePath) {
    if (path !== route) {
      window.history.pushState(null, "", path);
      setRoute(path);
    }
  }

  function handleAuthenticated(data: AuthData) {
    setUser({
      ...data.user,
      videoCount: 0,
      likedCount: 0
    });
    setAuthOpen(false);
    setAuthVersion((value) => value + 1);
  }

  async function handleLogout() {
    await logout();
    setUser(null);
    setAuthVersion((value) => value + 1);
    setAuthOpen(true);
    navigate("/");
  }

  function handlePublished(_video: VideoPostResponse) {
    setAuthVersion((value) => value + 1);
    navigate("/me");
  }

  return (
    <div className={appClassName}>
      <TopBar route={route} user={user} onNavigate={navigate} onAuthClick={() => setAuthOpen(true)} />
      {route === "/" && <RecommendPage authVersion={authVersion} onRequireAuth={openAuth} />}
      {route === "/me" && (
        <ProfilePage authVersion={authVersion} onRequireAuth={openAuth} onLogout={() => void handleLogout()} />
      )}
      {route === "/upload" && <UploadPage onRequireAuth={openAuth} onPublished={handlePublished} />}
      <AuthModal open={authOpen} onAuthenticated={handleAuthenticated} onClose={isLoggedIn() ? () => setAuthOpen(false) : undefined} />
    </div>
  );
}
