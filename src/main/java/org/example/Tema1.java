package org.example;

import java.util.ArrayList;
import java.util.List;

public class Tema1 {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java Tema1 <number_of_threads> <articles_file> <auxiliary_file>");
            return;
        }

        int threadNumber = Integer.parseInt(args[0]);
        String articlesFile = args[1];
        String auxiliaryFile = args[2];

        FilesParser filesParser = new FilesParser(articlesFile, auxiliaryFile);
        List<String> articles = new ArrayList<>(filesParser.parseArticles());
        List<String> languages = new ArrayList<>(filesParser.parseLanguages());
        List<String> categories = new ArrayList<>(filesParser.parseCategories());
        List<String> linkingWords = new ArrayList<>(filesParser.parseLinkingWords());

        Context context = new Context(threadNumber, articles, categories, languages, linkingWords);

        List<Thread> threads = new ArrayList<>(threadNumber);
        for (int i = 0; i < threadNumber; i++) {
            Thread thread = new NewsThread(i, context);
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}