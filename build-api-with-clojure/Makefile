# ./Makefile
# $ make static-check

.PHONY: format
format: ## フォーマット
	cljstyle check

.PHONY: lint
lint: ## リント
	clj-kondo --lint src

.PHONY: static-check
static-check: format lint

.PHONY: test
test:
	bin/kaocha --fail-fast

.PHONY: clean
clean:
	rm -fr target/

.PHONY: build
build: clean
	clojure -T:build uber
