package ru.netology;

import org.apache.http.NameValuePair;

import java.util.List;
import java.util.stream.Collectors;

public class Request {

    private final RequestLine requestLine;
    private final List<String> headers;
    private String body;

    private List<NameValuePair> queryParams;

    public Request(RequestLine requestLine, List<String> headers) {
        this.requestLine = requestLine;
        this.headers = headers;
    }

    public Request(RequestLine requestLine, List<String> headers, String body) {
        this.requestLine = requestLine;
        this.headers = headers;
        this.body = body;
    }

    public RequestLine getRequestLine() {
        return requestLine;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
    public List<NameValuePair> getQueryParams() {
        return queryParams;
    }
    public List<String> getQueryParams(String name) {
        return queryParams.stream()
                .filter(o -> o.getName().startsWith(name))
                .map(NameValuePair::getValue)
                .collect(Collectors.toList());
    }
    public void setQueryParams(List<NameValuePair> queryParams) {
        this.queryParams = queryParams;
    }
}
