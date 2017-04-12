package com.github.lexek.opengraph.fetcher;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Service
public class FetcherService {
    private final Logger logger = LoggerFactory.getLogger(FetcherService.class);
    private final HttpClient httpClient;
    private final AsyncLoadingCache<String, Mono<Map<String, String>>> cache;

    private long maxBodySize = 8388608;
    private long maxSupportedRedirects = 1;
    private boolean handleNonStandardPorts = false;

    public FetcherService() {
        this.httpClient = HttpClient.create(
            options -> {
                options.sslSupport();
                options.afterChannelInit(channel ->
                    //need this to handle compressed responses
                    channel.pipeline().addBefore("reactor.right.reactiveBridge", "decompressor", new HttpContentDecompressor())
                );
            }
        );
        this.cache = Caffeine
            .<String, Mono<Map<String, String>>>newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .buildAsync(url -> validateUrlAndFetch(url, 0));
    }

    public Mono<Map<String, String>> fetch(String url) {
        if (StringUtils.isEmpty(url)) {
            return Mono.just(ImmutableMap.of("error", "Url is not present"));
        }
        return Mono
            .fromFuture(cache.get(url))
            .then(Function.identity());
    }

    private Mono<Map<String, String>> validateUrlAndFetch(String url, int redirect) {
        try {
            return doFetch(new URL(url), redirect);
        } catch (MalformedURLException e) {
            return Mono.just(ImmutableMap.of("error", "Incorrect url"));
        }
    }

    private Mono<Map<String, String>> doFetch(URL url, int redirectNumber) {
        if (redirectNumber > maxSupportedRedirects) {
            return Mono.just(ImmutableMap.of(
                "error", "Too many consequent redirects"
            ));
        }
        if (!handleNonStandardPorts) {
            int port = url.getPort();
            if (port != -1) {
                if (url.getProtocol().equals("http") && port != 80 || url.getProtocol().equals("https") && port != 443) {
                    return Mono.just(ImmutableMap.of(
                        "error", "Handling of non-standard ports is disabled"
                    ));
                }
            }
        }
        return httpClient
            .get(
                url.toExternalForm(),
                request -> request.failOnServerError(false).failOnClientError(false) //disable exceptions on 4xx & 5xx statuses
            )
            .map(ReactorRequestWrapper::new)
            .then(response -> handleResponse(url, response, redirectNumber))
            .timeout(Duration.of(10, ChronoUnit.SECONDS))
            .retry(3)
            .doOnError(throwable -> logger.warn("caught exception", throwable))
            .otherwise((e) -> Mono.just(ImmutableMap.of(
                "error", e.getMessage() != null ? e.getMessage() : e.getClass().toString()
            )))
            .cache();
    }

    private Mono<Map<String, String>> handleResponse(URL url, ReactorRequestWrapper response, int redirectNumber) {
        int status = response.status();
        if (status == 301 || status == 302) {
            String location = response.headers().get(HttpHeaderNames.LOCATION);
            if (location == null) {
                response.dispose();
                return Mono.just(ImmutableMap.of("error", "Http redirect without location header"));
            }
            response.dispose();
            return validateUrlAndFetch(location, redirectNumber + 1);
        }
        if (status != 200) {
            response.dispose();
            return Mono.just(ImmutableMap.of("error", "Invalid response status " + status));
        }
        CharSequence mimeType = HttpUtil.getMimeType(response);
        if (mimeType == null) {
            response.dispose();
            return Mono.just(ImmutableMap.of("error", "No content-type"));
        }
        if (!Objects.equals(mimeType, "text/html")) {
            response.dispose();
            return Mono.just(ImmutableMap.of(
                "error", "Unsupported content-type",
                "mime", mimeType.toString()
            ));
        }
        long contentLength = HttpUtil.getContentLength(response, -1);
        if (contentLength > maxBodySize) {
            response.dispose();
            return Mono.just(ImmutableMap.of(
                "error", "Content is too big",
                "mime", mimeType.toString()
            ));
        }
        return readyBody(response)
            .cast(String.class)
            .map(Jsoup::parse)
            .map(FetcherService::parseBody)
            .doOnNext(result -> {
                if (!result.containsKey("error")) {
                    result.put("hostname", url.getHost());
                    result.put("mime", mimeType.toString());
                }
            });
    }

    private Mono<String> readyBody(ReactorRequestWrapper response) {
        Charset charset = HttpUtil.getCharset(response, StandardCharsets.UTF_8);
        return Mono.using(
            PooledByteBufAllocator.DEFAULT::buffer,
            buffer -> response
                .originalResponse()
                .receiveContent()
                .map((content) -> buffer.writeBytes(content.content()))
                .all((ignore) -> buffer.readableBytes() < maxBodySize)
                .then()
                .then(() -> Mono.just(buffer.toString(charset))),
            ByteBuf::release
        );
    }

    private static Map<String, String> parseBody(Document document) {
        Map<String, String> result = new HashMap<>();
        for (Element element : document.head().children()) {
            String tag = element.tag().getName();
            switch (tag) {
                case "meta": {
                    String property = element.attr("PROPERTY");
                    String content = element.attr("CONTENT");
                    if (property != null && content != null) {
                        if (property.startsWith("og:")) {
                            result.put(property, content);
                        }
                    }
                    break;
                }
                case "title": {
                    result.put("title", element.ownText());
                    break;
                }
            }
        }
        return result;
    }

    @Value("${og.maxBodySize ?: 8388608}")
    public void setMaxBodySize(long maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    @Value("${og.maxSupportedRedirects ?: 1}")
    public void setMaxSupportedRedirects(long maxSupportedRedirects) {
        this.maxSupportedRedirects = maxSupportedRedirects;
    }

    @Value("${og.handleNonStandardPorts ?: false}")
    public void setHandleNonStandardPorts(boolean handleNonStandardPorts) {
        this.handleNonStandardPorts = handleNonStandardPorts;
    }
}
