package com.popcorn.store.domain.popup.repository.view;

import java.time.LocalDateTime;
import java.util.UUID;

public interface PopupScheduleView {

	String getScheduleId();

	UUID getPopupId();

	LocalDateTime getStartAt();

	LocalDateTime getEndAt();

	Integer getPrice();

	Integer getCapacity();

	Integer getRemainingCapacity();

	Integer getReservationCapacity();

	Boolean getIsActive();
}
