package github.salemby.tododevops;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("postgres-test")
class PostgresTaskApiIT extends TaskApiContract {
}
