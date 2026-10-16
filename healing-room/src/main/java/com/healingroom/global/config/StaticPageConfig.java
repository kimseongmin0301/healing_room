package com.healingroom.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 폴더 주소로 들어오면 그 폴더의 index.html 을 보여준다 (스프링은 첫 화면 "/" 에서만 자동으로 해 준다).
 * 소개 페이지는 scripts/seo_pages.py 로 만든 정적 파일이다.
 */
@Configuration
public class StaticPageConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/themes", "/themes/");
        registry.addViewController("/themes/").setViewName("forward:/themes/index.html");
    }
}
