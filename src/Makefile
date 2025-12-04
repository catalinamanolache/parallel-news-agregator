.PHONY: clean build run

JAVA_ARGS?=$(ARGS)

clean:
	mvn -q clean

build:
	mvn -q package

run:
	@if [ -z "$(JAVA_ARGS)" ]; then \
	  echo "Usage: make run ARGS=\"<threads> <articles_file> <inputs_file>\""; \
	  exit 1; \
	fi
	mvn -q exec:java -Dexec.args="$(JAVA_ARGS)"