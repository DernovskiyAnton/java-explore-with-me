package ru.practicum.ewm.user.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.practicum.ewm.user.model.User;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Поиск пользователей по списку ID с пагинацией
     */
    List<User> findByIdIn(List<Long> ids, Pageable pageable);

    /**
     * Проверка существования пользователя по email
     */
    boolean existsByEmail(String email);
}