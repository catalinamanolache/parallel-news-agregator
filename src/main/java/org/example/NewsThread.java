package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;

public class NewsThread extends Thread {
    private int threadId;
    private Context context;
    private ObjectMapper objectMapper;

//    private static final int CHUNK_SIZE = 500;

    public NewsThread(int threadId, Context context) {
        this.threadId = threadId;
        this.context = context;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(){
        // phase 1: read all articles and add them to the shared list
        readAllArticles();

        try {
            context.barrier.await();

            // phase 2: parse articles, remove duplicates, sort and compute statistics
            if (threadId == 0) {
                processArticles();
                createTasks();
            }

            context.barrier.await();
            // phase 3: compute keywords statistics and write logs to files
            while (true) {
                Runnable task = context.tasks.poll();
                if (task == null) {
                    break;
                }
                task.run();
            }

            context.barrier.await();

            if (threadId == 0) {
                sortKeywords();
                writeReports();
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }

    }

    private void sortKeywords() {
        List<Map.Entry<String, Integer>> sortedList = new ArrayList<>(context.keywordsFreq.entrySet());

        sortedList.sort(new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                // descending by article count
                int res = o2.getValue().compareTo(o1.getValue());

                // ascending by word
                if (res == 0) {
                    return o1.getKey().compareTo(o2.getKey());
                }
                return res;
            }
        });

        if (!sortedList.isEmpty()) {
            context.topKeywordName = sortedList.get(0).getKey();
            context.topKeywordArticles = sortedList.get(0).getValue();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("keywords_count.txt"))) {
            for (Map.Entry<String, Integer> entry : sortedList) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createTasks() {
        context.tasks.add(this::writeAllArticles);

        for (String language : context.getLanguages()) {
            context.tasks.add(() -> writeLanguage(language));
        }

        for (String category : context.getCategories()) {
            context.tasks.add(() -> writeCategory(category));
        }


        List<Article> englishArticles = new ArrayList<>();
        for (Article article : context.sortedArticlesUuid) {
            if (article.getLanguage().equals("english")) {
                englishArticles.add(article);
            }
        }

        int size = englishArticles.size();
//        int CHUNK_SIZE = Math.max(1, size / context.getThreadCount());
        int CHUNK_SIZE = 250;
        for (int start = 0; start < size; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, size);

            // each thread parses a different subsection of the list
            List<Article> section = new ArrayList<>(englishArticles.subList(start, end));
            context.tasks.add(() -> parseKeywordsSection(section));
        }
    }

    private void parseKeywordsSection(List<Article> section) {
        Set<String> linkingWords = context.getLinkingWords();

        for (Article article : section) {
            if (article.getText() == null) {
                continue;
            }

            String rawText = article.getText().toLowerCase();
            String[] splitText = rawText.split("\\s+");
            Set<String> wordSet = new HashSet<>();

            for (String token : splitText) {
//                String word = token.replaceAll("[^a-z]", "");
                String word = fastClean(token);
                if (!word.isEmpty() && !linkingWords.contains(word)) {
                    wordSet.add(word);
                }
            }

            for (String word : wordSet) {
                this.context.keywordsFreq.merge(word, 1, Integer::sum);
            }
        }
    }

    private String fastClean(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void readAllArticles(){
        while (true) {
            // get the next available file index
            int idx = this.context.fileIndex.getAndIncrement();

            if (idx >= this.context.getJSONArticles().size()) {
                break;
            }

            // open the current article and read its contents
            String articlePath = this.context.getJSONArticles().get(idx);
            File articleFile = new File(articlePath);

            try {
                // read all articles from the current file and add them
                List<Article> articles = this.objectMapper.readValue(articleFile, new TypeReference<List<Article>>() {
                });

                for (Article article : articles) {
                    String uuid = article.getUuid();
                    String title = article.getTitle();

                    this.context.uuidFreq.merge(uuid, 1, Integer::sum);
                    this.context.titleFreq.merge(title, 1, Integer::sum);
                }
                this.context.allArticles.addAll(articles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processArticles() {
//        Map<String, Integer> uuidFreq = new HashMap<>();
//        Map<String, Integer> titleFreq = new HashMap<>();
//
//        for (Article article : context.allArticles) {
//            incrementMapCount(uuidFreq, article.getUuid());
//            incrementMapCount(titleFreq, article.getTitle());
//        }

        List<Article> uniqueArticles = new ArrayList<>();

        Map<String, Integer> authorCount = new HashMap<>();
        Map<String, Integer> languageCount = new HashMap<>();
        Map<String, Integer> categoryCount = new HashMap<>();

        for (Article article : context.allArticles) {
            String uuid = article.getUuid();
            String title = article.getTitle();

            if (context.uuidFreq.get(uuid) == 1 && context.titleFreq.get(title) == 1) {
                uniqueArticles.add(article);

                // increment this author's total articles count, language count
                // and all categories count
                incrementMapCount(authorCount, article.getAuthor());
                incrementMapCount(languageCount, article.getLanguage());
                for (String category : article.getCategories()) {
                    if (context.getCategories().contains(category)) {
                        incrementMapCount(categoryCount, category);
                    }
                }
            }
        }

        // sort all articles ascending by uuid
        context.sortedArticlesUuid = new ArrayList<>(uniqueArticles);
        context.sortedArticlesUuid.sort(new Comparator<Article>() {
            @Override
            public int compare(Article o1, Article o2) {
                return o1.getUuid().compareTo(o2.getUuid());
            }
        });

        context.sortedArticlesPublished = new ArrayList<>(uniqueArticles);
        context.sortedArticlesPublished.sort(new Comparator<Article>() {
            @Override
            public int compare(Article o1, Article o2) {
                // descending by published
                int result = o2.getPublished().compareTo(o1.getPublished());

                if (result == 0) {
                    // ascending by uuid
                    return o1.getUuid().compareTo(o2.getUuid());
                } else {
                    return result;
                }
            }
        });

        // set statistics variables
        context.uniqueArticles = uniqueArticles.size();
        context.duplicatesFound = context.allArticles.size() - uniqueArticles.size();

        Map.Entry<String, Integer> bestAuthorData = getMaxFromMap(authorCount);
        context.bestAuthorName = bestAuthorData.getKey();
        context.bestAuthorArticles = bestAuthorData.getValue();

        Map.Entry<String, Integer> topLanguageData = getMaxFromMap(languageCount);
        context.topLanguageName = topLanguageData.getKey();
        context.topLanguageArticles = topLanguageData.getValue();

        Map.Entry<String, Integer> topCategoryData = getMaxFromMap(categoryCount);
        context.topCategoryName = topCategoryData.getKey().replaceAll(",", "").replaceAll("\\s+", "_");
        context.topCategoryArticles = topCategoryData.getValue();

        Article mostRecentArticle = context.sortedArticlesPublished.get(0);
        for (Article article : context.sortedArticlesPublished) {
            if (article.getPublished().equals(mostRecentArticle.getPublished())) {
                if (article.getUuid().compareTo(mostRecentArticle.getUuid()) < 0) {
                    mostRecentArticle = article;
                }
            } else {
                break;
            }
        }
        context.mostRecentArticle = mostRecentArticle;
    }

    private void writeLanguage(String language) {
        String filename = language + ".txt";
        List<Article> articlesInLanguage = new ArrayList<>();

        for (Article article : context.sortedArticlesUuid) {
            if (article.getLanguage().equals(language)) {
                articlesInLanguage.add(article);
            }
        }

        if (articlesInLanguage.isEmpty()) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (Article article : articlesInLanguage) {
                writer.write(article.getUuid());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeCategory(String category) {
        String normalizedCategory = category.replaceAll(",", "").replaceAll("\\s+", "_");
        String filename = normalizedCategory + ".txt";

        List<Article> articlesInCategory = new ArrayList<>();
        for (Article article : context.sortedArticlesUuid) {
            if (article.getCategories().contains(category)) {
                articlesInCategory.add(article);
            }
        }

        if (articlesInCategory.isEmpty()) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (Article article : articlesInCategory) {
                writer.write(article.getUuid());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeAllArticles() {
        String filename = "all_articles.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (Article article : context.sortedArticlesPublished) {
                writer.write(article.getUuid() + " " + article.getPublished());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeReports() {
        String filename = "reports.txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("duplicates_found - " + context.duplicatesFound);
            writer.newLine();

            writer.write("unique_articles - " + context.uniqueArticles);
            writer.newLine();

            writer.write("best_author - " + context.bestAuthorName + " " + context.bestAuthorArticles);
            writer.newLine();

            writer.write("top_language - " + context.topLanguageName + " " + context.topLanguageArticles);
            writer.newLine();

            writer.write("top_category - " + context.topCategoryName + " " + context.topCategoryArticles);
            writer.newLine();

            writer.write("most_recent_article - " + context.mostRecentArticle.getPublished() + " " + context.mostRecentArticle.getUrl());
            writer.newLine();

            writer.write("top_keyword_en - " + context.topKeywordName + " " + context.topKeywordArticles);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void incrementMapCount(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private AbstractMap.SimpleEntry<String, Integer> getMaxFromMap(Map<String, Integer> map) {
        int maxValue = 0;
        String maxKey = "";
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() > maxValue) {
                maxValue = entry.getValue();
                maxKey = entry.getKey();
            } else if (entry.getValue() == maxValue) {
                if (maxKey.compareTo(entry.getKey()) > 0) {
                    maxKey = entry.getKey();
                }
            }
        }

        return new AbstractMap.SimpleEntry<>(maxKey, maxValue);
    }

    private void logArticles(String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))){
            writer.write("Total articles parsed: " + context.allArticles.size());
            writer.newLine();

            writer.write("Total unique articles: " + context.uniqueArticles);
            writer.newLine();

            writer.write("Total duplicate articles: " + context.duplicatesFound);
            writer.newLine();

            writer.write("Best author: " + context.bestAuthorName + " - " + context.bestAuthorArticles);
            writer.newLine();

            writer.write("Top language: " + context.topLanguageName + " - " + context.topLanguageArticles);
            writer.newLine();

            writer.write("Top category: " + context.topCategoryName + " - " + context.topCategoryArticles);
            writer.newLine();

            writer.write("Most recent article: " + context.mostRecentArticle.getPublished() + "  " + context.mostRecentArticle.getUrl());
            writer.newLine();

            writer.write("--------------------------------------------------");
            writer.newLine();

            writer.write("Total sorted articles by uuid: " + context.sortedArticlesUuid.size());
            writer.newLine();
            for (Article a : context.sortedArticlesUuid) {
                // Verificam null pentru a evita NullPointerException la printare
                String uuid = a.getUuid() != null ? a.getUuid() : "null";
                String title = a.getTitle() != null ? a.getTitle() : "null";

                writer.write(uuid + " | " + title);
                writer.newLine();
            }

            writer.write("Total sorted articles by published: " + context.sortedArticlesPublished.size());
            writer.newLine();
            for (Article a : context.sortedArticlesPublished) {
                // Verificam null pentru a evita NullPointerException la printare
                String published = a.getPublished() != null ? a.getPublished() : "null";
                String uuid = a.getUuid() != null ? a.getUuid() : "null";

                writer.write(uuid + " | " + published);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
