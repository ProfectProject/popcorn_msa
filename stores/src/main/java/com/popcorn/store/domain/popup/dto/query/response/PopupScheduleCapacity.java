package com.popcorn.store.domain.popup.dto.query.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PopupScheduleCapacity {

    private UUID scheduleId;
    private Integer capacity;
    private Integer remainingCapacity;

}
