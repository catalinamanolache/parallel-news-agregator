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
            }

            context.barrier.await();
            // phase 3: compute keywords statistics and write logs to files
            if (threadId == 0) {
                for (String language : context.getLanguages()) {
                    writeLanguage(language);
                }

                for (String category : context.getCategories()) {
                    writeCategory(category);
                }

                writeAllArticles();
                writeReports();
            }

            // debug phase
            if (threadId == 0) {
                logArticles("logs.txt");
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }

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
                this.context.allArticles.addAll(articles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processArticles() {
        Map<String, Integer> uuidFreq = new HashMap<>();
        Map<String, Integer> titleFreq = new HashMap<>();

        for (Article article : context.allArticles) {
            incrementMapCount(uuidFreq, article.getUuid());
            incrementMapCount(titleFreq, article.getTitle());
        }

        List<Article> uniqueArticles = new ArrayList<>();

        Map<String, Integer> authorCount = new HashMap<>();
        Map<String, Integer> languageCount = new HashMap<>();
        Map<String, Integer> categoryCount = new HashMap<>();

        for (Article article : context.allArticles) {
            String uuid = article.getUuid();
            String title = article.getTitle();

            if (uuidFreq.get(uuid) == 1 && titleFreq.get(title) == 1) {
                uniqueArticles.add(article);

                // increment this author's total articles count, language count
                // and all categories count
                incrementMapCount(authorCount, article.getAuthor());
                incrementMapCount(languageCount, article.getLanguage());
                for (String category : article.getCategories()) {
                    incrementMapCount(categoryCount, category);
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
        context.topCategoryName = topCategoryData.getKey();
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
