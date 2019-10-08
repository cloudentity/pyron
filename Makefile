CURRENT_VERSION	?= $(shell ./bin/get_version.sh)

NO_COLOR         = \x1b[0m
OK_COLOR         = \x1b[32;01m

.PHONY: all
all: standalone docker

standalone:
	@echo -e "$(OK_COLOR) > building standalone $(NO_COLOR)"
	mvn clean install -Pbuild-standalone

docker: standalone
	@echo -e "$(OK_COLOR) > building docker $(NO_COLOR)"
	docker build -t cloudentity/pyron pyron-app --build-arg version=$(CURRENT_VERSION)
