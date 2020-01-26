all:
	@echo "Usage:"
	@echo "make run # starts up the HTTP server for the app"
	@echo "make repl # starts a REPL"

run:
	clj -m quotes.main

repl:
	clj -A:nREPL
