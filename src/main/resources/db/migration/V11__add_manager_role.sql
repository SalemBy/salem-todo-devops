-- #69: MANAGER sits between OWNER and MEMBER. V8 declared the role check inline, so PostgreSQL named it.
ALTER TABLE project_memberships DROP CONSTRAINT project_memberships_role_check;
ALTER TABLE project_memberships ADD CONSTRAINT ck_project_memberships_role
    CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER', 'VIEWER'));
