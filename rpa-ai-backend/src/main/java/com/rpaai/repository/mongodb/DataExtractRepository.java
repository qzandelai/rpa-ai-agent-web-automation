package com.rpaai.repository.mongodb;

import com.rpaai.entity.mongodb.DataExtractRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataExtractRepository extends MongoRepository<DataExtractRecord, String> {

    List<DataExtractRecord> findByTaskIdOrderByExtractTimeDesc(Long taskId);

    List<DataExtractRecord> findByExecutionId(String executionId);

    List<DataExtractRecord> findTop50ByOrderByExtractTimeDesc();
}