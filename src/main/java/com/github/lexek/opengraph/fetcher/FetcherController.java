package com.github.lexek.opengraph.fetcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FetcherController {
    private final FetcherService fetcherService;

    @Autowired
    public FetcherController(FetcherService fetcherService) {
        this.fetcherService = fetcherService;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> getOgAttributes(@RequestBody FetchRequest request) {
        return fetcherService.fetch(request.getUrl());
    }
}
