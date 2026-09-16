package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.request.RegisterUserRequest;
import com.tsh11.fypcode.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthPageController {

    private final UserService userService;

    public AuthPageController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerUserRequest", new RegisterUserRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterUserRequest registerUserRequest,
                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.register(registerUserRequest);
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("email", "email.exists", ex.getMessage());
            return "auth/register";
        }

        return "redirect:/login?registered";
    }
}