# ./Makefile
# $ make static-check

#.PHONY: static-check
static-check: format lint

#.PHONY: format
format: ## フォーマット
	cljstyle check

#.PHONY: lint
lint: ## リント
	clj-kondo --lint src

.PHONY: clean
clean:
	rm -fr target/

.PHONY: build
build: clean
	clojure -T:build uber
