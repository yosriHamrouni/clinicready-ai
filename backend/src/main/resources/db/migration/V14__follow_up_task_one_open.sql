-- One open follow-up per appointment and requirement (YOS-35, PRD FR-E4). FollowUpService looks for an open
-- task before creating one; this index makes that hold under concurrent runs too. A task stops counting
-- once REJECTED or RESOLVED, so a new one can be proposed after that.

CREATE UNIQUE INDEX follow_up_task_one_open_idx ON follow_up_task (appointment_id, requirement_code)
    WHERE status IN ('PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'DELIVERED');
