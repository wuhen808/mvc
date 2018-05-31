package com.wuhen.demo.controller;

import com.wuhen.demo.servcie.TestService;
import com.wuhen.mvc.anno.Autowired;
import com.wuhen.mvc.anno.Controller;
import com.wuhen.mvc.anno.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
public class TestController {
    @Autowired("testService")
    private TestService testService;

    @RequestMapping("/test.html")
    public void test(HttpServletRequest request, HttpServletResponse response) throws IOException {
        System.out.println(testService);
        response.getWriter().println(testService.hello(request.getParameter("name")));
    }
}
