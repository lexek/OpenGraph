package lexek.fetcher;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchRequest {
    private String url;

    public String getUrl() {
        return url;
    }

    @JsonProperty
    public void setUrl(String url) {
        this.url = url;
    }
}
