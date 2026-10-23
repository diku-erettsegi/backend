CREATE TABLE courses (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  start_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  sessions INT NOT NULL,
  lessons_per_session INT NOT NULL,
  capacity INT NOT NULL,
  registration_open BOOLEAN NOT NULL DEFAULT TRUE,
  base_total INT NOT NULL,
  CONSTRAINT ck_courses_capacity CHECK (capacity > 0),
  CONSTRAINT ck_courses_price CHECK (base_total >= 0)
);
INSERT INTO courses (slug,name,start_date,start_time,end_time,sessions,lessons_per_session,capacity,registration_open,base_total) VALUES
('szombat-delelott','Szombat délelőtti csoport','2027-01-09','09:00:00','13:00:00',20,4,24,TRUE,280000),
('szombat-delutan','Szombat délutáni csoport','2027-01-09','14:00:00','18:00:00',20,4,24,TRUE,280000);

-- Nullable historical fields preserve every application received before groups/prices existed.
ALTER TABLE applications
  ADD COLUMN course_id BIGINT NULL,
  ADD COLUMN request_id VARCHAR(36) NULL,
  ADD COLUMN request_hash VARCHAR(64) NULL,
  ADD COLUMN friend_count INT NOT NULL DEFAULT 0,
  ADD COLUMN referral_note VARCHAR(500) NULL,
  ADD COLUMN laptop_rental_requested BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN base_total INT NULL,
  ADD COLUMN discount_percent INT NULL,
  ADD COLUMN discount_label VARCHAR(120) NULL,
  ADD COLUMN quoted_total INT NULL,
  ADD COLUMN quoted_per_lesson INT NULL,
  ADD CONSTRAINT fk_applications_course FOREIGN KEY (course_id) REFERENCES courses(id),
  ADD CONSTRAINT uk_applications_request UNIQUE (request_id),
  ADD CONSTRAINT ck_applications_friends CHECK (friend_count BETWEEN 0 AND 10),
  ADD INDEX ix_applications_course_status (course_id,status);

CREATE TABLE programming_interests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(254) NOT NULL,
  request_id VARCHAR(36) NOT NULL UNIQUE,
  request_hash VARCHAR(64) NOT NULL,
  privacy_accepted_at DATETIME(6) NOT NULL,
  privacy_version VARCHAR(40) NOT NULL,
  created_at DATETIME(6) NOT NULL
);
CREATE TABLE mail_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  interest_id BIGINT NOT NULL UNIQUE,
  preview_url VARCHAR(2048) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6) NOT NULL,
  locked_at DATETIME(6) NULL,
  claim_token VARCHAR(36) NULL,
  sent_at DATETIME(6) NULL,
  last_error_code VARCHAR(80) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_outbox_interest FOREIGN KEY (interest_id) REFERENCES programming_interests(id),
  CONSTRAINT ck_outbox_status CHECK (status IN ('QUEUED','SENDING','SENT','FAILED')),
  INDEX ix_outbox_due (status,next_attempt_at)
);
