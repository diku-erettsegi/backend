-- Preserve already recorded quotes exactly; V2 quotes only contained the period discount.
ALTER TABLE applications
  ADD COLUMN referred_by_friend BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN period_discount_percent INT NULL,
  ADD COLUMN friend_discount_percent INT NULL;
UPDATE applications SET period_discount_percent=discount_percent,friend_discount_percent=0 WHERE discount_percent IS NOT NULL;

ALTER TABLE programming_interests
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NEW',
  ADD COLUMN notes TEXT NULL,
  ADD COLUMN updated_at DATETIME(6) NULL,
  ADD CONSTRAINT ck_interests_status CHECK (status IN ('NEW','CONTACTED','COMPLETED'));
UPDATE programming_interests SET updated_at=created_at;
ALTER TABLE programming_interests MODIFY COLUMN updated_at DATETIME(6) NOT NULL;
-- mail_outbox remains historical data only. The application no longer maps, writes or processes it.
