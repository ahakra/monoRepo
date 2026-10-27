.DEFAULT_GOAL := help

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
ENV_FILE ?= .env
ENV_PATH := $(if $(filter /%,$(ENV_FILE)),$(ENV_FILE),$(CURDIR)/$(ENV_FILE))
COMPOSE_FILE := $(ROOT_DIR)docker/compose.postgres.yml
NPM ?= npm
MVN ?= ./mvnw
DOCKER ?= docker

COMPOSE = $(DOCKER) compose --env-file "$(ENV_PATH)" -f "$(COMPOSE_FILE)"

.PHONY: help check-env frontend frontend-install backend docker docker-up docker-down docker-logs docker-ps

help:
	@printf '%s\n' \
	  'Run these shortcuts from the repository root:' \
	  '  make frontend-install  Install frontend dependencies with npm ci' \
	  '  make frontend          Run the Vite frontend' \
	  '  make backend           Run the Spring Boot backend' \
	  '  make docker            Start PostgreSQL and pgAdmin in the background' \
	  '  make docker-up         Alias for make docker' \
	  '  make docker-down       Stop Docker services, keeping data volumes' \
	  '  make docker-logs       Follow Docker service logs' \
	  '  make docker-ps         Show Docker service status' \
	  '' \
	  'Backend and Docker use ENV_FILE=.env by default.' \
	  'Example: make backend ENV_FILE=.env.local' \
	  'Example: make docker ENV_FILE=.env.local'

check-env:
	@test -f "$(ENV_PATH)" || { printf '%s\n' "Environment file not found: $(ENV_PATH)" 'Create it from .env.example or set ENV_FILE=/path/to/file.' >&2; exit 1; }

frontend-install:
	cd "$(ROOT_DIR)apps/workflow-designer" && $(NPM) ci

frontend:
	cd "$(ROOT_DIR)apps/workflow-designer" && $(NPM) run dev

backend: check-env
	cd "$(ROOT_DIR)services/camel-integration" && SPRING_CONFIG_IMPORT="file:$(ENV_PATH)[.properties]" $(MVN) spring-boot:run

docker: docker-up

docker-up: check-env
	$(COMPOSE) up -d

docker-down: check-env
	$(COMPOSE) down

docker-logs: check-env
	$(COMPOSE) logs -f

docker-ps: check-env
	$(COMPOSE) ps
