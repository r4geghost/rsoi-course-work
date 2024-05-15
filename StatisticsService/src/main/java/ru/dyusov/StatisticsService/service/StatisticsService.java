package ru.dyusov.StatisticsService.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.dyusov.StatisticsService.model.Message;
import ru.dyusov.StatisticsService.repository.StatisticsRepository;

import java.util.List;

@Service
public class StatisticsService {
    private final StatisticsRepository repo;

    @Autowired
    public StatisticsService(StatisticsRepository repo) {
        this.repo = repo;
    }
    
    public void process(Message msg) {
        repo.save(msg);
    }

    public List<Message> select() {
        return repo.findAll();
    }

//    public List<ServiceAvg> selectServiceAvgTime() {
//        return repo.selectServiceAvgTime();
//    }
//
//    public List<QueryServiceAvg> selectQueryAvgTime() {
//        return repo.selectQueryAvgTime();
//    }
}
