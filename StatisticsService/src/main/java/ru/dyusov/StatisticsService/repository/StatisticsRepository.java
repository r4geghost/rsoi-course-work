package ru.dyusov.StatisticsService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.dyusov.StatisticsService.model.LogMessage;

@Repository
public interface StatisticsRepository extends JpaRepository<LogMessage, Integer> { }
