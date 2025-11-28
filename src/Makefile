## Makefile (WSL-friendly)
#SRC_DIR := ../src
#BIN_DIR := bin
#JAVAC := javac
#JAVA := java
#MAIN := Tema1
#
## collect .java sources recursively (works in WSL)
#SRCS := $(shell find "$(SRC_DIR)" -type f -name '*.java' 2>/dev/null)
#SRCS := $(strip $(SRCS))
#
#ifeq ($(strip $(SRCS)),)
#$(error No .java sources found in $(SRC_DIR) - create Java files under $(SRC_DIR) or adjust SRC_DIR)
#endif
#
#.PHONY: all build run clean
#all: build
#
#build:
#	@mkdir -p "$(BIN_DIR)"
#	$(JAVAC) -d "$(BIN_DIR)" $(SRCS)
#
#run: build
#	$(JAVA) -cp "$(BIN_DIR)" $(MAIN) $(ARGS)
#
#clean:
#	-rm -rf "$(BIN_DIR)"

## Use Maven to build and run
## Maven Makefile
#MVN ?= mvn
#MAIN_CLASS ?= org.example.Tema1
#
#.PHONY: deps build compile test package install run clean all
#
#all: build
#
#deps:
#	$(MVN) dependency:resolve
#
#build: package
#
#compile:
#	$(MVN) -q compile
#
#test:
#	$(MVN) -q test
#
#package:
#	$(MVN) -q package
#
#install:
#	$(MVN) -q install
#
#run: compile
#	$(MVN) -q exec:java -Dexec.mainClass=$(MAIN_CLASS)
#
#clean:
#	$(MVN) -q clean

# Simple wrapper Makefile used by checker.sh

.PHONY: clean build run

# Forward ARGS from checker to Maven exec
JAVA_ARGS?=$(ARGS)

clean:
	mvn -q -f .. clean

build:
	mvn -q -f .. package

run:
	@if [ -z "$(JAVA_ARGS)" ]; then \
	  echo "Usage: make run ARGS=\"<threads> <articles_file> <inputs_file>\""; \
	  exit 1; \
	fi
	mvn -q -f .. exec:java -Dexec.args="$(JAVA_ARGS)"