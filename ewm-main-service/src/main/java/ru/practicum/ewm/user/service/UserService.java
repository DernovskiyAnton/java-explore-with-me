package ru.practicum.ewm.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.user.dto.NewUserRequest;
import ru.practicum.ewm.user.dto.UserDto;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Регистрация нового пользователя
     */
    @Transactional
    public UserDto registerUser(NewUserRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Проверка уникальности email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email " + request.getEmail() + " is already in use");
        }

        User user = userMapper.toEntity(request);
        User savedUser = userRepository.save(user);

        log.info("User registered successfully with id: {}", savedUser.getId());
        return userMapper.toDto(savedUser);
    }

    /**
     * Получение списка пользователей
     * @param ids список ID пользователей (если null - все пользователи)
     * @param from количество элементов для пропуска
     * @param size размер страницы
     */
    public List<UserDto> getUsers(List<Long> ids, Integer from, Integer size) {
        log.info("Getting users: ids={}, from={}, size={}", ids, from, size);

        Pageable pageable = PageRequest.of(from / size, size);
        List<User> users;

        if (ids != null && !ids.isEmpty()) {
            users = userRepository.findByIdIn(ids, pageable);
        } else {
            users = userRepository.findAll(pageable).getContent();
        }

        return users.stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Удаление пользователя
     */
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user with id: {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User with id=" + userId + " was not found");
        }

        userRepository.deleteById(userId);
        log.info("User with id={} deleted successfully", userId);
    }

    /**
     * Получение пользователя по ID (внутренний метод)
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }
}