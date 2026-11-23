package com.example.backend.ai;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "chat_record_action")
class ChatRecordAction {
    @Id
    UUID actionId;

    @Column(nullable = false, length = 64)
    String requestDigest;

    @Column(nullable = false)
    Long billId;

    protected ChatRecordAction() {}
}
