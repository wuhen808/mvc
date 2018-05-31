package com.wuhen.demo.servcie.impl;

import com.wuhen.demo.servcie.TestService;
import com.wuhen.mvc.anno.Service;

@Service("testService")
public class TestServiceImpl implements TestService {
    @Override
    public String hello(String name) {
        return "hello "+name;
    }
}
