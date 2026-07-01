package org.example.uptodate.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class HomeController {
    @GetMapping("/")
    public String showHomePage(Model model) {
        model.addAttribute("appName", "UpToDate");
        model.addAttribute("tagline", "Capture the moment. Share the world");
        return "index";
    }

}
