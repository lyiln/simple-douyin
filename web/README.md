# Simple Douyin Web

React + Vite Web client for the course demo. It implements only the required flows:

- Recommended feed, view tracking, likes, comments.
- Upload video with multipart form data.
- My videos, pagination, delete own video, logout.

## Run

```powershell
cd web
npm install
npm run dev
```

The dev server proxies `/api` and `/uploads` to `VITE_PROXY_TARGET`.

For a deployed API server without proxy, set:

```text
VITE_API_BASE_URL=http://47.95.238.140:18090
```
