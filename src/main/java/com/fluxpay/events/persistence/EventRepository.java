package com.fluxpay.events.persistence;

import com.fluxpay.events.domain.Event;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface EventRepository extends Repository<Event, UUID> {

    Event save(Event event);
}
