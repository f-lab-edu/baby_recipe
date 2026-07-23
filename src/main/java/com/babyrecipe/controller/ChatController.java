package com.babyrecipe.controller;

import com.babyrecipe.dto.request.StartChatRequest;
import com.babyrecipe.dto.response.ApiResponse;
import com.babyrecipe.dto.response.ChatMessageResponse;
import com.babyrecipe.dto.response.ChatRoomResponse;
import com.babyrecipe.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getRooms(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(chatService.getRooms(userId)));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> startChat(
        @Valid @RequestBody StartChatRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(chatService.getOrCreateRoom(userId, request.getPartnerId())));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getMessages(
        @PathVariable Long roomId,
        @PageableDefault(size = 30) Pageable pageable,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(chatService.getMessages(roomId, userId, pageable)));
    }
}
