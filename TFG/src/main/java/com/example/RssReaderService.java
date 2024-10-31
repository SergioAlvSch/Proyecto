package com.example;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.util.List;

@Singleton
public class RssReaderService {

    public Mono<List<SyndEntry>> readRssFeed(String feedUrl) {
        return Mono.fromCallable(() -> {
            URL url = new URL(feedUrl);
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(url));
            return feed.getEntries();
        });
    }
}