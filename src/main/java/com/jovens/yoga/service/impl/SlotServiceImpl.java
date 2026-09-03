package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.AdminSlotRequest;
import com.jovens.yoga.dto.response.SlotResponse;
import com.jovens.yoga.entity.ClassSlot;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.mapper.SlotMapper;
import com.jovens.yoga.repository.SlotRepository;
import com.jovens.yoga.service.SlotService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final SlotMapper slotMapper;

    public SlotServiceImpl(SlotRepository slotRepository, SlotMapper slotMapper) {
        this.slotRepository = slotRepository;
        this.slotMapper = slotMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getActiveSlots() {
        return slotRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(slotMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> getAllSlotsForAdmin() {
        return slotRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(slotMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassSlot getActiveSlotEntity(Long id) {
        ClassSlot slot = findSlotOrThrow(id);
        if (!slot.isActive()) {
            throw new BusinessException("The selected class slot is not currently available.", "SLOT_INACTIVE", HttpStatus.BAD_REQUEST);
        }
        return slot;
    }

    @Override
    @Transactional(readOnly = true)
    public SlotResponse getSlotForAdmin(Long id) {
        return slotMapper.toResponse(findSlotOrThrow(id));
    }

    @Override
    @Transactional
    public SlotResponse createSlot(AdminSlotRequest request) {
        ClassSlot slot = new ClassSlot();
        applyRequest(slot, request);
        ClassSlot saved = slotRepository.save(slot);
        return slotMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SlotResponse updateSlot(Long id, AdminSlotRequest request) {
        ClassSlot slot = findSlotOrThrow(id);
        applyRequest(slot, request);
        return slotMapper.toResponse(slot);
    }

    @Override
    @Transactional
    public SlotResponse setActive(Long id, boolean active) {
        ClassSlot slot = findSlotOrThrow(id);
        slot.setActive(active);
        return slotMapper.toResponse(slot);
    }

    @Override
    @Transactional
    public void deleteSlot(Long id) {
        ClassSlot slot = findSlotOrThrow(id);
        slotRepository.delete(slot);
    }

    private ClassSlot findSlotOrThrow(Long id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class slot not found with id: " + id));
    }

    private void applyRequest(ClassSlot slot, AdminSlotRequest request) {
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setCapacity(request.capacity());
        slot.setLabel(request.label());
        slot.setActive(request.active());
        slot.setDisplayOrder(request.displayOrder());
    }
}
