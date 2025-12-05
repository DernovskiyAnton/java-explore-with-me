package ru.practicum.ewm.category.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.category.dto.CategoryDto;
import ru.practicum.ewm.category.dto.NewCategoryDto;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.repository.CategoryRepository;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository repository;
    private final CategoryMapper mapper;
    private final EventRepository eventRepository;

    @Transactional
    public CategoryDto addCategory(NewCategoryDto dto) {
        log.info("Adding new category: {}", dto.getName());

        if (repository.existsByName(dto.getName())) {
            throw new ConflictException("Category with name '" + dto.getName() + "' already exists");
        }

        Category category = mapper.toEntity(dto);
        Category saved = repository.save(category);

        log.info("Category added with id: {}", saved.getId());
        return mapper.toDto(saved);
    }

    @Transactional
    public CategoryDto updateCategory(Long catId, CategoryDto dto) {
        log.info("Updating category id: {}", catId);

        Category category = getCategoryById(catId);

        if (repository.existsByNameAndIdNot(dto.getName(), catId)) {
            throw new ConflictException("Category with name '" + dto.getName() + "' already exists");
        }

        category.setName(dto.getName());
        Category updated = repository.save(category);

        log.info("Category updated: {}", updated.getName());
        return mapper.toDto(updated);
    }

    @Transactional
    public void deleteCategory(Long catId) {
        log.info("Deleting category id: {}", catId);

        Category category = getCategoryById(catId);

        // Проверка что категория не используется в событиях
        if (eventRepository.existsByCategoryId(catId)) {
            throw new ConflictException("The category is not empty");
        }

        repository.delete(category);
        log.info("Category deleted: {}", catId);
    }

    public List<CategoryDto> getCategories(Integer from, Integer size) {
        log.info("Getting categories: from={}, size={}", from, size);

        PageRequest pageRequest = PageRequest.of(from / size, size);

        return repository.findAll(pageRequest).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public CategoryDto getCategory(Long catId) {
        log.info("Getting category id: {}", catId);

        Category category = getCategoryById(catId);
        return mapper.toDto(category);
    }

    private Category getCategoryById(Long catId) {
        return repository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}