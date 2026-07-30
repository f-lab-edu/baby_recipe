package com.babyrecipe.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class SpaController {

    // ws는 반드시 제외해야 한다. RequestMappingHandlerMapping(order 0)이 STOMP의
    // WebSocketHandlerMapping(order 1)보다 먼저 조회되므로, 여기서 /ws가 잡히면
    // 핸드셰이크가 index.html forward로 가로채여 101 대신 200이 응답된다.
    @RequestMapping(
        value = {"/{path:^(?!uploads$|assets$|ws$)[^\\.]*$}", "/{path:^(?!uploads$|assets$|ws$)[^\\.]*$}/**"},
        method = RequestMethod.GET
    )
    public String forwardToIndex(@PathVariable String path) {
        return "forward:/index.html";
    }
}
