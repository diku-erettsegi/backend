-- Additive rollout only. No historical personal data is erased during migration.
ALTER TABLE applications
  DROP CONSTRAINT ck_applications_status,
  ADD CONSTRAINT ck_applications_status CHECK (status IN ('NEW','CONTACTED','WAITLISTED','ACCEPTED','REJECTED','WITHDRAWN')),
  ADD COLUMN closed_at DATETIME(6) NULL,
  ADD COLUMN retention_protected BOOLEAN NOT NULL DEFAULT FALSE,
  ADD INDEX ix_applications_closed (closed_at,status);

-- Unknown historical closure time starts a fresh grace period, never backdated to submission.
UPDATE applications SET closed_at=UTC_TIMESTAMP(6) WHERE status IN ('REJECTED','WITHDRAWN');
UPDATE applications SET retention_protected=TRUE
  WHERE contract_confirmed=TRUE OR paid_training_total>0 OR full_payment_date IS NOT NULL OR status='ACCEPTED';

ALTER TABLE programming_interests
  ADD COLUMN completed_at DATETIME(6) NULL,
  ADD INDEX ix_interests_completed (completed_at,status);
UPDATE programming_interests SET completed_at=UTC_TIMESTAMP(6) WHERE status='COMPLETED';

ALTER TABLE users ADD COLUMN access_ended_at DATETIME(6) NULL;
