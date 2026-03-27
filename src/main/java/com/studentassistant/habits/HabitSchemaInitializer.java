package com.studentassistant.habits;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class HabitSchemaInitializer {

    private final HabitService habitService;

    public HabitSchemaInitializer(HabitService habitService) {
        this.habitService = habitService;
    }

    @PostConstruct
    public void createTables() {
        habitService.initSchema();
    }
}
