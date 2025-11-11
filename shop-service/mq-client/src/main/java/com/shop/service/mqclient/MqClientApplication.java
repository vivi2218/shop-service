package com.shop.service.mqclient;

import com.shop.service.module.util.BannerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.shop.service.mqclient"})
@EnableFeignClients(basePackages = {"com.shop.service.mqclient.feign"})
public class MqClientApplication {

    public static void main(String[] args) {

        SpringApplication springApplication = new SpringApplication(MqClientApplication.class);
        springApplication.setBanner(new BannerBuilder());
        springApplication.run(args);
    }

}
