package com.research.AIagent.config;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
@ConfigurationProperties(prefix = "proxy")
@Getter
@Setter
@Slf4j
public class ProxyConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private String userAgent;

    public boolean isConfigured() {
        return host != null && !host.isBlank() && port > 0;
    }

    public Proxy toProxy(){
        if (!isConfigured()) {
            log.info("No proxy configured – using direct connection");
            return Proxy.NO_PROXY;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }
}
