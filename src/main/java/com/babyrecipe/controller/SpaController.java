package com.babyrecipe.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    @RequestMapping(value = {"/{path:^(?!uploads$|assets$)[^\\.]*$}", "/{path:^(?!uploads$|assets$)[^\\.]*$}/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
