package com.jovens.yoga.repository;

import com.jovens.yoga.entity.ClassSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlotRepository extends JpaRepository<ClassSlot, Long> {
    List<ClassSlot> findByActiveTrueOrderByDisplayOrderAsc();
    List<ClassSlot> findAllByOrderByDisplayOrderAsc();
}
