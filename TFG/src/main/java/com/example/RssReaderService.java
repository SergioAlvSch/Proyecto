package com.example;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class RssReaderService {
    private List<SyndEntry> lastEntries = new ArrayList<>();

    public Flux<List<SyndEntry>> readRssFeed(String feedUrl) {
        return Flux.defer(() -> {
            try {
                URL url = new URL(feedUrl);
                SyndFeedInput input = new SyndFeedInput();
                SyndFeed feed = input.build(new XmlReader(url));
                lastEntries = feed.getEntries();
                return Flux.just(lastEntries);
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    public List<SyndEntry> getLastEntries() {
        return lastEntries;
    }
}