CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(16) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  auth_version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT ck_users_role CHECK (role IN ('ADMIN','STUDENT'))
);
CREATE TABLE applications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  student_name VARCHAR(120) NOT NULL,
  email VARCHAR(254) NOT NULL,
  phone VARCHAR(40) NOT NULL,
  parent_name VARCHAR(120),
  message TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  notes TEXT,
  privacy_accepted_at DATETIME(6) NOT NULL,
  privacy_version VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT ck_applications_status CHECK (status IN ('NEW','CONTACTED','ACCEPTED','REJECTED')),
  INDEX ix_applications_created (created_at)
);
CREATE TABLE lessons (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  description TEXT,
  starts_at DATETIME(6),
  meeting_url VARCHAR(2048),
  published BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE lesson_files (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  lesson_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  storage_name VARCHAR(36) NOT NULL,
  size BIGINT NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_files_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
  CONSTRAINT uk_files_storage UNIQUE (storage_name)
);
CREATE TABLE audit_events (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor VARCHAR(254) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target VARCHAR(160) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX ix_audit_created (created_at)
);
