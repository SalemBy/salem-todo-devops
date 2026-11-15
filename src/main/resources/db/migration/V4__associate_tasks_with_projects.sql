-- Requires an empty pre-association tasks table. No projects are created automatically.
ALTER TABLE tasks ADD COLUMN project_id BIGINT NOT NULL;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id);
CREATE INDEX idx_tasks_project_id ON tasks(project_id);
