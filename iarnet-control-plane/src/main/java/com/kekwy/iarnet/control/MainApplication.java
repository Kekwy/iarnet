package com.kekwy.iarnet.control;


import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class MainApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(MainApplication.class, args);
    }

    @Component
    public static class Initializer implements ApplicationRunner {
        @Override
        public void run(ApplicationArguments args) throws Exception {
            init();
        }

        public static void init() {
            // 启动时初始化数据库
            // 创建管理员用户
            // 创建默认应用
        }

    }


}
