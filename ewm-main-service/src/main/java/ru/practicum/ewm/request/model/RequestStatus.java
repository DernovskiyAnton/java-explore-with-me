package ru.practicum.ewm.request.model;

public enum RequestStatus {
    PENDING,    // Ожидает рассмотрения
    CONFIRMED,  // Подтверждено
    REJECTED,   // Отклонено
    CANCELED    // Отменено инициатором заявки
}