package com.dentalManagement.dentalFlowBackend.enums;

public enum SseEventType {

    // ── Order mutations ────────────────────────────────────────
    ORDER_CREATED,           // lab broadcast + doctor
    ORDER_STAGE_UPDATED,     // lab broadcast + doctor
    ORDER_DELIVERED,         // lab broadcast + doctor
    ORDER_UPDATED,           // lab broadcast + doctor
    ORDER_DELETED,           // lab broadcast + doctor

    // ── Doctor mutations ───────────────────────────────────────
    DOCTOR_ADDED,            // lab broadcast
    DOCTOR_UPDATED,          // lab broadcast
    DOCTOR_DELETED,          // lab broadcast

    // ── Dentist order requests ─────────────────────────────────
    DENTIST_REQUEST_RECEIVED, // lab broadcast
    DENTIST_REQUEST_REMOVED,  // lab broadcast OR targeted doctor

    // ── User admin actions (targeted) ──────────────────────────
    USER_DEACTIVATED,        // targeted user only
    USER_DELETED,            // targeted user only
    ROLE_CHANGED             // targeted user only — high priority (instant role refresh)
}
