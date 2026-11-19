package com.tiet.docket.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React handles page routes (/student/..., /staff/...) in the browser, so any
 * non-API path without a file extension is answered with the React app.
 */
@Controller
public class SpaController {

    @GetMapping({"/{path:^(?!api|h2-console|assets)[^\\.]*}",
                 "/{path:^(?!api|h2-console|assets)[^\\.]*}/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
