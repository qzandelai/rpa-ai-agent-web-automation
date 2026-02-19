package com.rpaai.repository;

import com.rpaai.entity.AutomationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationTaskRepository extends JpaRepository<AutomationTask, Long> {
}