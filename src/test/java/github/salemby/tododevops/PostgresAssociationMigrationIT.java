package github.salemby.tododevops;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PostgresAssociationMigrationIT {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void requiresEmptyOldTasksAndEnforcesOwnership(boolean existingTasks) throws Exception {
        String url=System.getenv("TEST_DB_URL"), user=System.getenv("TEST_DB_USERNAME"), password=System.getenv("TEST_DB_PASSWORD");
        String schema="association_"+UUID.randomUUID().toString().replace("-", "");
        var flyway=Flyway.configure().dataSource(url,user,password).schemas(schema).cleanDisabled(false).load();
        try {
            Flyway.configure().dataSource(url,user,password).schemas(schema).target("3").load().migrate();
            try(var c=DriverManager.getConnection(url,user,password)) {
                c.setSchema(schema);
                try(var s=c.createStatement()) {
                    s.executeUpdate("INSERT INTO projects(name,project_key,created_at,updated_at) VALUES ('Existing','EXIST',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
                    if(existingTasks) s.executeUpdate("INSERT INTO tasks(title,description,status,active,created_at,updated_at) VALUES ('One','Details','PENDING',TRUE,'2025-01-01T00:00:00Z','2025-01-02T00:00:00Z'),('Two',NULL,'COMPLETED',FALSE,'2025-02-01T00:00:00Z','2025-02-02T00:00:00Z')");
                    var before=rows(s,"SELECT id,title,description,status,active,created_at,updated_at FROM tasks ORDER BY id");
                    var projects=rows(s,"SELECT * FROM projects ORDER BY id");
                    if(existingTasks) {
                        assertThrows(org.flywaydb.core.api.FlywayException.class, flyway::migrate);
                        assertEquals(before,rows(s,"SELECT id,title,description,status,active,created_at,updated_at FROM tasks ORDER BY id"));
                        assertEquals(projects,rows(s,"SELECT * FROM projects ORDER BY id"));
                        return;
                    }
                    flyway.migrate();
                    assertEquals(projects,rows(s,"SELECT * FROM projects ORDER BY id"), "Migration must not create system projects");
                    assertEquals(0,rows(s,"SELECT * FROM tasks").size());
                    s.executeUpdate("INSERT INTO tasks(title,status,created_at,updated_at,project_id) SELECT 'Owned','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,id FROM projects WHERE project_key='EXIST'");
                    assertEquals("23502",assertThrows(SQLException.class,()->s.executeUpdate("UPDATE tasks SET project_id=NULL")).getSQLState());
                    assertEquals("23503",assertThrows(SQLException.class,()->s.executeUpdate("UPDATE tasks SET project_id=9223372036854775807")).getSQLState());
                    assertEquals("23503",assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM projects WHERE project_key='EXIST'")).getSQLState());
                    assertEquals(1,rows(s,"SELECT indexname FROM pg_indexes WHERE schemaname='"+schema+"' AND indexname='idx_tasks_project_id'").size());
                }
            }
            assertTrue(flyway.validateWithResult().validationSuccessful);
        } finally {flyway.clean();}
    }
    private List<List<Object>> rows(Statement s,String sql) throws SQLException {
        var rows=new ArrayList<List<Object>>();
        try(var r=s.executeQuery(sql)) {while(r.next()){var row=new ArrayList<Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.add(r.getObject(i));rows.add(row);}}
        return rows;
    }
}
