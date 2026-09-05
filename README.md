# Parallel News Aggregator

This project is a multi-threaded Java application that reads, processes, and aggregates news articles efficiently. The architecture relies on synchronized parallel processing to distribute workloads dynamically.

## Parallelization Strategy

The main application thread initially reads and parses input files, storing shared variables in a centralized `Context` class. The core processing is then divided into four distinct stages, synchronized using a `CyclicBarrier`.

*   **Phase 1 (Parallel Reading):** Threads dynamically claim JSON files using an `AtomicInteger` to prevent faster threads from blocking. Each thread reads its assigned articles and records UUID and title frequencies in local maps. Upon completion, these local maps are merged into global `ConcurrentHashMap` variables to minimize synchronization overhead.
*   **Phase 2 (Filtering & Task Creation):** Threads process their local list of articles to eliminate duplicates by referencing the globally populated frequency maps. Thread 0 then sequentially merges these partial lists, calculates necessary statistics, and sorts the articles. Finally, Thread 0 populates a `ConcurrentLinkedQueue` with grouped tasks (chunks of 20 to 1000 articles) for file writing and keyword parsing.
*   **Phase 3 (Parallel Processing):** All available threads consume tasks from the shared queue until it is empty. These tasks involve writing specific files (like language or category data) and parsing English text to populate a global `ConcurrentHashMap` of keyword frequencies.
*   **Phase 4 (Sequential Finalization):** Thread 0 takes the fully populated keyword frequency map, converts it into a list, and sorts it in descending order by occurrences (and lexicographically in case of a tie). It then writes the final output to `keywords_count.txt` and `reports.txt`.

## Synchronization Mechanisms

*   **CyclicBarrier:** Ensures strict delimitation between execution phases and guarantees data consistency before moving to the next stage.
*   **AtomicInteger:** Distributes files dynamically among threads and tracks the total number of duplicate articles found.
*   **ConcurrentHashMap:** Safely stores global frequency data for UUIDs, titles, and keywords across multiple threads.
*   **ConcurrentLinkedQueue:** Manages the producer-consumer task queue for parallel file writing and text parsing operations.

## Performance and Scalability

Testing was conducted on a system featuring an AMD Ryzen 5 7640HS (6 physical / 12 logical cores), 16 GB DDR5 RAM, Windows 11 WSL2, and Java Azul Zulu 17. The evaluation utilized a dataset containing 11,031 articles, 19 languages, 24 categories, and 140 linking words.

| Threads (p) | Average Time (s) | Speedup (S) | Efficiency (E) |
| :--- | :--- | :--- | :--- |
| **1** | 7.226 | 1.0x | 100% |
| **2** | 4.774 | 1.51x | 75% |
| **4** | 3.764 | 1.92x | 48% |

*   Execution time decreases as the thread count increases, with the most significant performance gain occurring when upgrading from 1 to 2 threads.
*   Scalability is primarily bottlenecked by I/O operations (reading/writing to the disk) and the strictly sequential operations performed by Thread 0.
