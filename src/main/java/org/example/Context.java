package org.example;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class Context {
    // input data variables
    public int threadCount;
    public List<String> JSONArticles;
    public Set<String> categories;
    public List<String> languages;
    public Set<String> linkingWords;

    // statistics variables
    public AtomicInteger duplicatesFound;
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

    // partial results from each thread
    public List<List<Article>> partialUniqueArticles;
    public List<Map<String, Integer>> partialAuthorFreq;
    public List<Map<String, Integer>> partialLanguageFreq;
    public List<Map<String, Integer>> partialCategoryFreq;

    // atomic integer for the current JSON file index to parse in phase 1
    public AtomicInteger fileIndex;

    // global structures for the frequency of articles' uuid and titles
    public ConcurrentHashMap<String, Integer> uuidFreq;
    public ConcurrentHashMap<String, Integer> titleFreq;

    // structure for the final articles, sorted ascending by uuid and without duplicates
    public List<Article> sortedArticlesUuid;

    // structure for the final articles, sorted descending by published and without duplicates
    public List<Article> sortedArticlesPublished;

    // hashmap for english keywords frequency
    public ConcurrentHashMap<String, Integer> keywordsFreq;

    // tasks queue for phase 3
    public ConcurrentLinkedQueue<Runnable> tasks;

    // barrier for threads synchronization after each phase
    public CyclicBarrier barrier;

    public Context(int threadCount, List<String> JSONArticles, List<String> categories,
                   List<String> languages, List<String> linkingWords) {
        this.threadCount = threadCount;
        this.JSONArticles = JSONArticles;
        this.languages = languages;
        this.linkingWords = new HashSet<>(linkingWords);
        this.barrier = new CyclicBarrier(threadCount);
        this.categories = new HashSet<>(categories);
        this.keywordsFreq = new ConcurrentHashMap<>();
        this.tasks = new ConcurrentLinkedQueue<>();
        this.uuidFreq = new ConcurrentHashMap<>();
        this.titleFreq = new ConcurrentHashMap<>();
        this.fileIndex = new AtomicInteger(0);
        this.duplicatesFound = new AtomicInteger(0);
        this.partialUniqueArticles = new ArrayList<>(Collections.nCopies(threadCount, null));
        this.partialAuthorFreq = new ArrayList<>(Collections.nCopies(threadCount, null));
        this.partialLanguageFreq = new ArrayList<>(Collections.nCopies(threadCount, null));
        this.partialCategoryFreq = new ArrayList<>(Collections.nCopies(threadCount, null));
    }
}
