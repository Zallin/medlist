.PHONY: test

figwheel:
	clj -A:cljs:figwheel -m "figwheel.main" -b "dev"

repl:
	clj -A:clj -m "nrepl"

test:
	make docker
	clj -A:clj:dev -m "cognitect.test-runner" -d "backend/test" -d "ui/test"
