package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FilesParser {
    private String articlesPath;
    private String auxiliaryPath;

    public FilesParser(String articlesPath, String auxiliaryPath) {
        this.articlesPath = articlesPath;
        this.auxiliaryPath = auxiliaryPath;
    }

    public List<String> parseArticles() {
        try {
            Path articlesFile = Paths.get(articlesPath).normalize();
            Path parentDir = articlesFile.getParent();

            List<String> lines = Files.readAllLines(articlesFile).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            int expectedCount = Integer.parseInt(lines.get(0));

            List<String> articlePaths = lines.stream()
                    .skip(1)
                    .limit(expectedCount)
                    .collect(Collectors.toList());

            return articlePaths.stream()
                    .map(p -> {if (parentDir == null) return p;
                        return parentDir.resolve(p).normalize().toString();})
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.out.println("Error reading `articles` file: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> parseLanguages() {
        try {
            Path auxFile = Paths.get(auxiliaryPath).normalize();

            List<String> auxLines = Files.readAllLines(auxFile).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // languages.txt is always the first entry (index 0)
            String languagesToken = auxLines.get(1);

            Path languagesPath = (auxFile.getParent() == null)
                    ? Paths.get(languagesToken).normalize()
                    : auxFile.getParent().resolve(languagesToken).normalize();

            List<String> lines = Files.readAllLines(languagesPath);

            int expectedCount = Integer.parseInt(lines.get(0).trim());

            List<String> languages = lines.stream()
                    .skip(1)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(expectedCount)
                    .collect(Collectors.toList());
            return languages;
        } catch (IOException e) {
            System.out.println("Error parsing languages: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> parseCategories() {
        try {
            Path auxFile = Paths.get(auxiliaryPath).normalize();

            List<String> auxLines = Files.readAllLines(auxFile).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // categories.txt is always the third entry (index 2)
            String categoriesToken = auxLines.get(2);

            Path categoriesPath = (auxFile.getParent() == null)
                    ? Paths.get(categoriesToken).normalize()
                    : auxFile.getParent().resolve(categoriesToken).normalize();

            List<String> lines = Files.readAllLines(categoriesPath);

            // first line is the number of categories
            int expectedCount = Integer.parseInt(lines.get(0).trim());

            List<String> categories = lines.stream()
                    .skip(1)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(expectedCount)
                    .collect(Collectors.toList());
            return categories;
        } catch (IOException e) {
            System.out.println("Error parsing categories: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> parseLinkingWords() {
        try {
            Path auxFile = Paths.get(auxiliaryPath).normalize();

            List<String> auxLines = Files.readAllLines(auxFile).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // linking_words.txt is always the second entry (index 1)
            String linkingWordsToken = auxLines.get(3);

            Path linkingWordsPath = (auxFile.getParent() == null)
                    ? Paths.get(linkingWordsToken).normalize()
                    : auxFile.getParent().resolve(linkingWordsToken).normalize();

            List<String> lines = Files.readAllLines(linkingWordsPath);

            int expectedCount = Integer.parseInt(lines.get(0).trim());

            List<String> linkingWords = lines.stream()
                    .skip(1)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> !s.isEmpty())
                    .limit(expectedCount)
                    .collect(Collectors.toList());

            return linkingWords;
        } catch (IOException e) {
            System.out.println("Error parsing linking words: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
