-- 1) RECYCLED becomes a distinct terminal state alongside RECOVERED.
--    "Recovered" records that material came back into the loop; "recycled" records that it was
--    accepted into a recycling process. Both are terminal; neither can be left once set.
ALTER TABLE pickup_requests DROP CONSTRAINT pickup_requests_status_check;
ALTER TABLE pickup_requests ADD CONSTRAINT pickup_requests_status_check
    CHECK (status IN ('REQUESTED', 'ACCEPTED', 'SCHEDULED', 'PICKED_UP', 'PROCESSING', 'RECOVERED', 'RECYCLED', 'CANCELLED'));

-- 2) Optimistic locking. Two collectors accepting the same request used to both succeed: the
--    service read the row, checked it was unassigned, and wrote — with nothing serialising the
--    pair. A version column makes the second writer's UPDATE match no rows, so the loser gets a
--    conflict instead of a false "accepted" confirmation and a duplicate notification.
ALTER TABLE pickup_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 3) One collector organisation per account. The application checked for an existing application
--    before inserting, which is a check-then-act race; the database now enforces it.
--    (PostgreSQL allows multiple NULLs in a unique index, so unlinked partner rows are unaffected.)
CREATE UNIQUE INDEX ux_collection_partners_user ON collection_partners (user_id);
