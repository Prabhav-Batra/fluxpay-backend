package com.fluxpay.events.service;

import java.util.List;

public record EventDetailView(EventView event, List<DeliveryView> deliveries) {}
