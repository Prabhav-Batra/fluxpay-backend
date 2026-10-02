package com.fluxpay.sales.service;

public interface ReconciliationService {

    /** Asks the gateway about older sessions with an order, recording any captured payment we missed. */
    int reconcile();
}
