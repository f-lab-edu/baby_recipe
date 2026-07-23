package com.babyrecipe.dto.response;

import com.babyrecipe.domain.ChatMessage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
    private Long roomId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
            .id(message.getId())
            .roomId(message.getChatRoom().getId())
            .senderId(message.getSender().getId())
            .content(message.getContent())
            .createdAt(message.getCreatedAt())
            .build();
    }
}
