package github.salemby.tododevops.workmanagement.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The shared page/size rules for paged list endpoints. */
final class Paging {
    private Paging() {}

    static void require(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Page must be nonnegative, size 1-100, and offset at most 2147483647");
    }
}
