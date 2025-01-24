package de.unibayreuth.se.taskboard;

import de.unibayreuth.se.taskboard.business.domain.Task;
import de.unibayreuth.se.taskboard.business.domain.User;
import de.unibayreuth.se.taskboard.business.ports.TaskService;
import de.unibayreuth.se.taskboard.business.ports.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Load initial data into the list via the list service from the business layer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
class LoadInitialData implements InitializingBean {
    private final TaskService taskService;
    private final UserService userService;

    @Override
    public void afterPropertiesSet() {
        log.info("Deleting existing data...");
        userService.clear();
        taskService.clear();
        log.info("Loading initial data...");
        List<User> users = TestFixtures.createUsers(userService);
        List<Task> tasks = TestFixtures.createTasks(taskService);
        Task task1 = tasks.get(0);
        task1.setAssigneeId(users.get(0).getId());
        taskService.upsert(task1);
        Task task2 = tasks.get(tasks.size() - 1);
        task2.setAssigneeId(users.get(tasks.size() - 1).getId());
        taskService.upsert(task2);
    }
}