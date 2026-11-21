-- One optional assignee per task (#10). The composite key keeps the assignee within the
-- task's project members whatever writes the row; existing tasks start unassigned.
ALTER TABLE tasks ADD COLUMN assignee_account_id BIGINT;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_assignee_membership
    FOREIGN KEY (project_id, assignee_account_id)
    REFERENCES project_memberships(project_id, account_id);
CREATE INDEX idx_tasks_project_assignee ON tasks(project_id, assignee_account_id);
