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

    // this thread's local articles
    private List<Article> threadArticles;

    public NewsThread(int threadId, Context context) {
        this.threadId = threadId;
        this.context = context;
        this.objectMapper = new ObjectMapper();
        this.threadArticles = new ArrayList<>();
    }

    @Override
    public void run(){
        try {
            // phase 1: each thread reads articles from files
            readArticlesLocally();
            context.barrier.await();

            // phase 2: each thread processes its local articles to remove duplicates and compute partial statistics
            processArticlesLocally();
            context.barrier.await();

            // thread 0 merges all partial results and creates tasks for phase 3
            if (threadId == 0) {
                mergeAndSortArticles();
                createTasks();
            }
            context.barrier.await();

            // phase 3: each thread executes tasks from the shared tasks queue (writing files, parsing keywords)
            while (true) {
                Runnable task = context.tasks.poll();
                if (task == null) {
                    break;
                }
                task.run();
            }
            context.barrier.await();

            // phase 4: thread 0 sorts keywords and writes the final reports.txt file
            if (threadId == 0) {
                sortKeywords();
                writeReports();
            }
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }
    }

    private void readArticlesLocally(){
        while (true) {
            // get the next available file index
            int idx = this.context.fileIndex.getAndIncrement();

            if (idx >= this.context.JSONArticles.size()) {
                break;
            }

            // open the current article and read its contents
            String articlePath = this.context.JSONArticles.get(idx);
            File articleFile = new File(articlePath);

            try {
                // read all articles from the current file and add them to the list
                List<Article> articles = this.objectMapper.readValue(articleFile,
                        new TypeReference<List<Article>>() {});

                threadArticles.addAll(articles);

                // update global frequency maps for duplicate detection
                for (Article article : articles) {
                    String uuid = article.getUuid();
                    String title = article.getTitle();

                    this.context.uuidFreq.merge(uuid, 1, Integer::sum);
                    this.context.titleFreq.merge(title, 1, Integer::sum);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processArticlesLocally() {
        List<Article> localUniqueArticles = new ArrayList<>();
        Map<String, Integer> localAuthorCount = new HashMap<>();
        Map<String, Integer> localLanguageCount = new HashMap<>();
        Map<String, Integer> localCategoryCount = new HashMap<>();
        int localDuplicates = 0;

        // process each article in this thread's local list
        for (Article article : threadArticles) {
            String uuid = article.getUuid();
            String title = article.getTitle();

            // check if the article is unique based on uuid and title frequency
            if (context.uuidFreq.get(uuid) == 1 && context.titleFreq.get(title) == 1) {
                localUniqueArticles.add(article);

                // increment this author's total articles count, language count
                // and all categories count
                incrementMapCount(localAuthorCount, article.getAuthor());
                incrementMapCount(localLanguageCount, article.getLanguage());
                for (String category : article.getCategories()) {
                    if (context.categories.contains(category)) {
                        incrementMapCount(localCategoryCount, category);
                    }
                }
            } else {
                localDuplicates++;
            }
        }

        // store this thread's partial results in the context variables
        context.partialUniqueArticles.set(threadId, localUniqueArticles);
        context.partialAuthorFreq.set(threadId, localAuthorCount);
        context.partialLanguageFreq.set(threadId, localLanguageCount);
        context.partialCategoryFreq.set(threadId, localCategoryCount);
        context.duplicatesFound.addAndGet(localDuplicates);
    }

    private void mergeAndSortArticles() {
        List<Article> uniqueArticles = new ArrayList<>();
        Map<String, Integer> authorCount = new HashMap<>();
        Map<String, Integer> languageCount = new HashMap<>();
        Map<String, Integer> categoryCount = new HashMap<>();

        // merge partial results from each thread
        for (int i = 0; i < context.threadCount; i++) {
            List<Article> threadUniqueArticles = context.partialUniqueArticles.get(i);
            Map<String, Integer> threadAuthorCount = context.partialAuthorFreq.get(i);
            Map<String, Integer> threadLanguageCount = context.partialLanguageFreq.get(i);
            Map<String, Integer> threadCategoryCount = context.partialCategoryFreq.get(i);

            // add this thread's unique articles to the global list
            uniqueArticles.addAll(threadUniqueArticles);

            // merge this thread's author, language and category counts to the global maps
            for (Map.Entry<String, Integer> entry : threadAuthorCount.entrySet()) {
                authorCount.put(entry.getKey(),
                        authorCount.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }

            for (Map.Entry<String, Integer> entry : threadLanguageCount.entrySet()) {
                languageCount.put(entry.getKey(),
                        languageCount.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }

            for (Map.Entry<String, Integer> entry : threadCategoryCount.entrySet()) {
                categoryCount.put(entry.getKey(),
                        categoryCount.getOrDefault(entry.getKey(), 0) + entry.getValue());
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

        // sort all articles descending by published, and ascending by uuid in case of equality
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

        Map.Entry<String, Integer> bestAuthorData = getMaxFromMap(authorCount);
        context.bestAuthorName = bestAuthorData.getKey();
        context.bestAuthorArticles = bestAuthorData.getValue();

        Map.Entry<String, Integer> topLanguageData = getMaxFromMap(languageCount);
        context.topLanguageName = topLanguageData.getKey();
        context.topLanguageArticles = topLanguageData.getValue();

        Map.Entry<String, Integer> topCategoryData = getMaxFromMap(categoryCount);
        context.topCategoryName = topCategoryData.getKey().replaceAll(",", "").replaceAll("\\s+", "_");
        context.topCategoryArticles = topCategoryData.getValue();

        // find the most recent article (first in sorted list), and in case of equality,
        // the one with the smallest uuid
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

    private void createTasks() {
        // add task for writing all_articles.txt
        context.tasks.add(this::writeAllArticles);

        // add tasks for writing language and category files
        for (String language : context.languages) {
            context.tasks.add(() -> writeLanguage(language));
        }

        for (String category : context.categories) {
            context.tasks.add(() -> writeCategory(category));
        }


        // prepare list of english articles for keyword parsing
        List<Article> englishArticles = new ArrayList<>();
        for (Article article : context.sortedArticlesUuid) {
            if (article.getLanguage().equals("english")) {
                englishArticles.add(article);
            }
        }

        // calculate chunk size based on number of articles and threads
        int size = englishArticles.size();
        int maxChunks = context.threadCount * 4;
        int chunkSize = size / maxChunks;

        // limit chunk size between 20 and 1000, to avoid too small or too large chunks
        int adjustedChunkSize = Math.max(20, Math.min(chunkSize, 1000));


        // add tasks for parsing keywords in english articles
        for (int start = 0; start < size; start += adjustedChunkSize) {
            int end = Math.min(start + adjustedChunkSize, size);

            // each thread parses a different subsection of the list
            List<Article> section = new ArrayList<>(englishArticles.subList(start, end));
            context.tasks.add(() -> parseKeywordsSection(section));
        }
    }

    private void sortKeywords() {
        List<Map.Entry<String, Integer>> sortedList =
                new ArrayList<>(context.keywordsFreq.entrySet());

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

        // set top keyword statistics
        context.topKeywordName = sortedList.get(0).getKey();
        context.topKeywordArticles = sortedList.get(0).getValue();

        // write keywords_count.txt file
        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter("keywords_count.txt"))) {
            for (Map.Entry<String, Integer> entry : sortedList) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void parseKeywordsSection(List<Article> section) {
        Set<String> linkingWords = context.linkingWords;

        // parse each article in the current section of articles
        for (Article article : section) {
            if (article.getText() == null) {
                continue;
            }

            // format the article text and split it into tokens
            String rawText = article.getText().toLowerCase();
            String[] splitText = rawText.split("\\s+");
            Set<String> wordSet = new HashSet<>();

            // use a set to keep track of all unique words in the article, that are not a linking word
            for (String token : splitText) {
                String word = cleanToken(token);
                if (!word.isEmpty() && !linkingWords.contains(word)) {
                    wordSet.add(word);
                }
            }

            // update global keywords frequency map with the words from this article
            for (String word : wordSet) {
                this.context.keywordsFreq.merge(word, 1, Integer::sum);
            }
        }
    }


    private void writeLanguage(String language) {
        String filename = language + ".txt";
        List<Article> articlesInLanguage = new ArrayList<>();

        // collect all articles in this language from the sorted list
        for (Article article : context.sortedArticlesUuid) {
            if (article.getLanguage().equals(language)) {
                articlesInLanguage.add(article);
            }
        }

        // only write the file if there are articles in this language
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
        String normalizedCategory = category
                                .replaceAll(",", "")
                                .replaceAll("\\s+", "_");
        String filename = normalizedCategory + ".txt";
        List<Article> articlesInCategory = new ArrayList<>();

        // collect all articles in this category from the sorted list
        for (Article article : context.sortedArticlesUuid) {
            if (article.getCategories().contains(category)) {
                articlesInCategory.add(article);
            }
        }

        // only write the file if there are articles in this category
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
            writer.write("duplicates_found - " + context.duplicatesFound.get());
            writer.newLine();

            writer.write("unique_articles - " + context.uniqueArticles);
            writer.newLine();

            writer.write("best_author - " + context.bestAuthorName
                        + " " + context.bestAuthorArticles);
            writer.newLine();

            writer.write("top_language - " + context.topLanguageName
                        + " " + context.topLanguageArticles);
            writer.newLine();

            writer.write("top_category - " + context.topCategoryName
                        + " " + context.topCategoryArticles);
            writer.newLine();

            writer.write("most_recent_article - " + context.mostRecentArticle.getPublished()
                        + " " + context.mostRecentArticle.getUrl());
            writer.newLine();

            writer.write("top_keyword_en - " + context.topKeywordName + " "
                        + context.topKeywordArticles);
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

    private String cleanToken(String token) {
        // keep only lowercase letters a-z
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
