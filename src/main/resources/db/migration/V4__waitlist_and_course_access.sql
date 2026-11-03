-- Additive upgrade: keep historical applications, price snapshots and files intact.
ALTER TABLE courses ADD COLUMN end_date DATE NULL;

-- Approved schedule: twenty Saturdays, excluding Pentecost weekend (May 15).
UPDATE courses SET end_date='2027-05-29' WHERE start_date='2027-01-09';
ALTER TABLE applications
  DROP CONSTRAINT ck_applications_status,
  ADD CONSTRAINT ck_applications_status CHECK (status IN ('NEW','CONTACTED','WAITLISTED','ACCEPTED','REJECTED')),
  ADD COLUMN age_group VARCHAR(20) NULL,
  ADD COLUMN guardian_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN waitlist_requested BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT ck_applications_age CHECK (age_group IS NULL OR age_group IN ('ADULT','MINOR_14_17'));
ALTER TABLE lessons
  ADD COLUMN course_id BIGINT NULL,
  ADD CONSTRAINT fk_lessons_course FOREIGN KEY (course_id) REFERENCES courses(id),
  ADD INDEX ix_lessons_course_published (course_id,published);
ALTER TABLE users
  ADD COLUMN application_id BIGINT NULL,
  ADD CONSTRAINT fk_users_application FOREIGN KEY (application_id) REFERENCES applications(id),
  ADD CONSTRAINT uk_users_application UNIQUE (application_id);

ALTER TABLE applications
    ADD COLUMN laptop_rental_fee INT NULL,
    ADD COLUMN contract_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN paid_training_total INT NOT NULL DEFAULT 0,
    ADD COLUMN full_payment_date DATE NULL,
    ADD COLUMN verified_friend_count INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_applications_payment CHECK (paid_training_total >= 0 AND verified_friend_count BETWEEN 0 AND 10);

CREATE TABLE course_dates (course_id BIGINT NOT NULL, session_order INT NOT NULL, session_date DATE NOT NULL, PRIMARY KEY(course_id,session_order), CONSTRAINT fk_dates_course FOREIGN KEY(course_id) REFERENCES courses(id));
INSERT INTO course_dates(course_id,session_order,session_date)
SELECT c.id,d.n,d.dt FROM courses c CROSS JOIN (
SELECT 0 AS n, CAST('2027-01-09' AS DATE) AS dt
UNION ALL
SELECT 1 AS n, CAST('2027-01-16' AS DATE) AS dt
UNION ALL
SELECT 2 AS n, CAST('2027-01-23' AS DATE) AS dt
UNION ALL
SELECT 3 AS n, CAST('2027-01-30' AS DATE) AS dt
UNION ALL
SELECT 4 AS n, CAST('2027-02-06' AS DATE) AS dt
UNION ALL
SELECT 5 AS n, CAST('2027-02-13' AS DATE) AS dt
UNION ALL
SELECT 6 AS n, CAST('2027-02-20' AS DATE) AS dt
UNION ALL
SELECT 7 AS n, CAST('2027-02-27' AS DATE) AS dt
UNION ALL
SELECT 8 AS n, CAST('2027-03-06' AS DATE) AS dt
UNION ALL
SELECT 9 AS n, CAST('2027-03-13' AS DATE) AS dt
UNION ALL
SELECT 10 AS n, CAST('2027-03-20' AS DATE) AS dt
UNION ALL
SELECT 11 AS n, CAST('2027-03-27' AS DATE) AS dt
UNION ALL
SELECT 12 AS n, CAST('2027-04-03' AS DATE) AS dt
UNION ALL
SELECT 13 AS n, CAST('2027-04-10' AS DATE) AS dt
UNION ALL
SELECT 14 AS n, CAST('2027-04-17' AS DATE) AS dt
UNION ALL
SELECT 15 AS n, CAST('2027-04-24' AS DATE) AS dt
UNION ALL
SELECT 16 AS n, CAST('2027-05-01' AS DATE) AS dt
UNION ALL
SELECT 17 AS n, CAST('2027-05-08' AS DATE) AS dt
UNION ALL
SELECT 18 AS n, CAST('2027-05-22' AS DATE) AS dt
UNION ALL
SELECT 19 AS n, CAST('2027-05-29' AS DATE) AS dt
) d WHERE c.start_date='2027-01-09';
