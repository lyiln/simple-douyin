CREATE DATABASE IF NOT EXISTS simple_douyin
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE simple_douyin;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS videos (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    author_id BIGINT UNSIGNED NOT NULL,
    caption VARCHAR(200) NOT NULL,
    video_url VARCHAR(512) NOT NULL,
    cover_url VARCHAR(512) NULL,
    duration_ms INT UNSIGNED NULL,
    like_count INT UNSIGNED NOT NULL DEFAULT 0,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED NOT NULL DEFAULT 0,
    visibility VARCHAR(20) NOT NULL DEFAULT 'public',
    status VARCHAR(20) NOT NULL DEFAULT 'published',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_videos_author_created (author_id, created_at DESC, id DESC),
    KEY idx_videos_recommend (status, visibility, deleted_at, like_count DESC, created_at DESC, id DESC),
    KEY idx_videos_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS video_likes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    video_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_likes_user_video (user_id, video_id),
    KEY idx_video_likes_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS video_views (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    video_id BIGINT UNSIGNED NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'recommended_feed',
    watch_duration_ms INT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_views_user_video (user_id, video_id),
    KEY idx_video_views_user_created (user_id, created_at DESC),
    KEY idx_video_views_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS request_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    `query` VARCHAR(1024) NULL,
    request_body TEXT NULL,
    response_body MEDIUMTEXT NULL,
    status_code INT NOT NULL,
    business_code INT NULL,
    duration_ms INT UNSIGNED NOT NULL,
    client_ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    error_message VARCHAR(1024) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_logs_request_id (request_id),
    KEY idx_request_logs_user_time (user_id, created_at DESC),
    KEY idx_request_logs_path_time (path, created_at DESC),
    KEY idx_request_logs_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    video_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    content VARCHAR(300) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_comments_video_created (video_id, created_at DESC, id DESC),
    KEY idx_comments_author_created (author_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
