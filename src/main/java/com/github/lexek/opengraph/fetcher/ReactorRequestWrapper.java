package com.github.lexek.opengraph.fetcher;

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpVersion;
import reactor.ipc.netty.http.client.HttpClientResponse;

/**
 * We need this wrapper to use netty {@link io.netty.handler.codec.http.HttpUtil} methods because reactor-netty doesn't
 * provide access to underlying netty {@link io.netty.handler.codec.http.HttpResponse} response
 **/
public class ReactorRequestWrapper implements HttpMessage {
    private final HttpClientResponse response;

    public ReactorRequestWrapper(HttpClientResponse response) {
        this.response = response;
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return response.version();
    }

    @Override
    public HttpVersion protocolVersion() {
        return response.version();
    }

    @Override
    public HttpMessage setProtocolVersion(HttpVersion version) {
        return this;
    }

    @Override
    public HttpHeaders headers() {
        return response.responseHeaders();
    }

    @Override
    public DecoderResult getDecoderResult() {
        return null;
    }

    @Override
    public DecoderResult decoderResult() {
        return null;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {

    }

    public int status() {
        return response.status().code();
    }

    public HttpClientResponse originalResponse() {
        return response;
    }

    public void dispose() {
        response.dispose();
    }
}
