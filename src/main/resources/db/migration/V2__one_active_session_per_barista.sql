-- A barista serves at most one ACTIVE chat session at a time. BaristaQueue already guarantees this
-- in memory; this index backs the same rule in the database, so a queue that is ever out of step
-- (a restart, a bug) cannot silently give one barista two customers.
CREATE UNIQUE INDEX uq_chat_sessions_active_barista ON chat_sessions(barista_id) WHERE status = 'ACTIVE';
