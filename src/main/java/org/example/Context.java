package org.example;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class Context {
    private int threadCount;
    private List<String> JSONArticles;
    private List<String> categories;
    private List<String> languages;
    public Set<String> linkingWords;

    // statistics variables
    public int duplicatesFound;
    public int uniqueArticles;
    public String bestAuthorName;
    public int bestAuthorArticles;
    public String topLanguageName;
    public int topLanguageArticles;
    public String topCategoryName;
    public int topCategoryArticles;
    public Article mostRecentArticle;
    public String topKeywordName;
    public int topKeywordArticles;

    // data structure for collecting all articles, unsorted and duplicated, from articles.txt
    public ConcurrentLinkedQueue<Article> allArticles;
    public AtomicInteger fileIndex;

    // data structure for the final articles, sorted ascending by uuid and without duplicates
    public List<Article> sortedArticlesUuid;

    // data structure for the final articles, sorted descending by published and without duplicates
    public List<Article> sortedArticlesPublished;

    // hashmap for english keywords frequency
    public ConcurrentHashMap<String, Integer> keywordsFreq;

    // tasks queue for phase 3
    public ConcurrentLinkedQueue<Runnable> tasks;

    // barrier for threads synchronization after each phase
    public CyclicBarrier barrier;

    public Context(int threadCount, List<String> JSONArticles, List<String> categories, List<String> languages, List<String> linkingWords) {
        this.threadCount = threadCount;
        this.JSONArticles = JSONArticles;
        this.languages = languages;
        this.linkingWords = new HashSet<>(linkingWords);
        this.barrier = new CyclicBarrier(threadCount);
        this.categories = categories;
        this.allArticles = new ConcurrentLinkedQueue<>();
        this.fileIndex = new AtomicInteger(0);
        this.keywordsFreq = new ConcurrentHashMap<>();
        this.tasks = new ConcurrentLinkedQueue<>();
    }

    public Set<String> getLinkingWords() {
        return linkingWords;
    }

    public void setLinkingWords(Set<String> linkingWords) {
        this.linkingWords = linkingWords;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getJSONArticles() {
        return JSONArticles;
    }

    public void setJSONArticles(List<String> JSONArticles) {
        this.JSONArticles = JSONArticles;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }
}
