package com.babyrecipe.controller;

import com.babyrecipe.dto.request.ChatMessageRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.ChatMessageResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void send(@Payload ChatMessageRequest request, Authentication authentication) {
        Long senderId = Long.parseLong(((UserDetails) authentication.getPrincipal()).getUsername());
        ChatMessageResponse response = chatService.sendMessage(request.getRoomId(), senderId, request.getContent());
        messagingTemplate.convertAndSend("/topic/chat." + request.getRoomId(), response);
    }

    @MessageExceptionHandler(BabyRecipeException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleChatException(BabyRecipeException e) {
        log.warn("STOMP chat error: {}", e.getMessage());
        return ApiResponse.error(e.getMessage());
    }
}
