package de.unibayreuth.se.taskboard.data.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibayreuth.se.taskboard.business.domain.Task;
import de.unibayreuth.se.taskboard.business.domain.TaskStatus;
import de.unibayreuth.se.taskboard.business.exceptions.TaskNotFoundException;
import de.unibayreuth.se.taskboard.business.ports.TaskPersistenceService;
import de.unibayreuth.se.taskboard.data.mapper.TaskEntityMapper;
import de.unibayreuth.se.taskboard.data.persistence.EventEntity;
import de.unibayreuth.se.taskboard.data.persistence.EventRepository;
import de.unibayreuth.se.taskboard.data.persistence.TaskEntity;
import de.unibayreuth.se.taskboard.data.persistence.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Event-sourcing-based implementation of the task persistence service that the business layer provides as a port.
 */
@Service
@RequiredArgsConstructor
@Primary
public class TaskPersistenceServiceEventSourcingImpl implements TaskPersistenceService {
    private final TaskRepository taskRepository;
    private final TaskEntityMapper taskEntityMapper;
    private final EventRepository eventRepository;
    @Override
    public void clear() {
        taskRepository.findAll()
                .forEach(taskEntity -> eventRepository.saveAndFlush(
                        EventEntity.deleteEventOf(taskEntityMapper.fromEntity(taskEntity), null))
                );
        if (taskRepository.count() != 0) {
            throw new IllegalStateException("Tasks not successfully deleted.");
        }
    }

    @NonNull
    @Override
    public List<Task> getAll() {
        return taskRepository.findAll().stream()
                .map(taskEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public Optional<Task> getById(@NonNull UUID id) {
        return taskRepository.findById(id)
                .map(taskEntityMapper::fromEntity);
    }

    @NonNull
    @Override
    public List<Task> getByStatus(@NonNull TaskStatus status) {
        return taskRepository.findByStatus(status).stream()
                .map(taskEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public List<Task> getByAssignee(@NonNull UUID userId) {
        return taskRepository.findByAssigneeId(userId).stream()
                .map(taskEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public Task upsert(@NonNull Task task) throws TaskNotFoundException {
        /*
        The upsert method in the TaskPersistenceServiceEventSourcingImpl class handles both the creation and updating of tasks.
        If the task ID is null, it creates a new task by generating a new UUID, saving an insert event, and returning the newly created task.
        If the task ID is not null, it updates the existing task by finding it in the repository, updating its fields, saving an update event, and returning the updated task.
        In both cases, it uses the EventRepository to log the changes and the TaskRepository to persist the task data.
        */

        ObjectMapper objectMapper = new ObjectMapper();

        if (task.getId() == null) {
            task.setId(UUID.randomUUID());

            TaskEntity taskEntity = taskEntityMapper.toEntity(task);
            taskRepository.save(taskEntity);

            EventEntity insertEvent = EventEntity.insertEventOf(task, task.getAssigneeId(), objectMapper);
            eventRepository.save(insertEvent);

            return task;
        } else {
            TaskEntity existingTask = taskRepository.findById(task.getId())
                    .orElseThrow(() -> new TaskNotFoundException("Task with ID " + task.getId() + " does not exist!"));

            existingTask.setAssigneeId(task.getAssigneeId());
            existingTask.setUpdatedAt(task.getUpdatedAt());
            existingTask.setDescription(task.getDescription());
            existingTask.setStatus(task.getStatus());
            existingTask.setTitle(task.getTitle());

            Task updatedTask = taskEntityMapper.fromEntity(existingTask);
            taskRepository.save(existingTask);

            EventEntity updateEvent = EventEntity.updateEventOf(updatedTask, task.getAssigneeId(), objectMapper);
            eventRepository.save(updateEvent);

            return updatedTask;
        }
    }

    @Override
    public void delete(@NonNull UUID id) throws TaskNotFoundException {
        /*
        The delete method in the TaskPersistenceServiceEventSourcingImpl class performs the following actions:
        Attempts to find a Task by its ID in the taskRepository.
        If the task is not found, it throws a TaskNotFoundException.
        If the task is found, it logs a delete event using the eventRepository.
        Checks if the task still exists in the taskRepository.
        If the task still exists, it throws an IllegalStateException indicating the task was not successfully deleted.
        */

        ObjectMapper objectMapper = new ObjectMapper();

        Optional<TaskEntity> task = taskRepository.findById(id);

        if (task.isEmpty()) {
            throw new TaskNotFoundException("Task with ID " + id + " does not exist!");
        } else {
            TaskEntity taskEntity = task.get();
            taskRepository.delete(taskEntity);

            EventEntity deleteEvent = EventEntity.updateEventOf(taskEntityMapper.fromEntity(taskEntity), taskEntity.getAssigneeId(), objectMapper);
            eventRepository.save(deleteEvent);
        }

        Optional<TaskEntity> deletedTask = taskRepository.findById(id);
        if (deletedTask.isPresent()) {
            throw new IllegalStateException("Deletion of task with ID " + id + " failed!");
        }
    }
}
