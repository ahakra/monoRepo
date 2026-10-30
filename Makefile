.DEFAULT_GOAL := help

ROOT_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
ENV_FILE ?= .env
ENV_PATH := $(if $(filter /%,$(ENV_FILE)),$(ENV_FILE),$(CURDIR)/$(ENV_FILE))
COMPOSE_FILE := $(ROOT_DIR)docker/compose.postgres.yml
NPM ?= npm
MVN ?= ./mvnw
DOCKER ?= docker
OLLAMA ?= ollama
MODEL ?= gemma4:e4b-it-q4_K_M
OLLAMA_BIND ?= 0.0.0.0:11434

COMPOSE = $(DOCKER) compose --env-file "$(ENV_PATH)" -f "$(COMPOSE_FILE)"
WEBUI_COMPOSE = $(DOCKER) compose --env-file "$(ENV_PATH)" -f "$(ROOT_DIR)docker/compose.webui.yml"

.PHONY: help check-env frontend frontend-install backend docker docker-up docker-down docker-logs docker-ps ollama-serve ollama ollama-gemma ollama-qwen webui webui-up webui-down webui-logs webui-ps

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
	  '  make ollama-serve      Run the local Ollama server (foreground)' \
	  '  make ollama            Run Gemma (override with MODEL=...)' \
	  '  make ollama-gemma      Run gemma4:e4b-it-q4_K_M' \
	  '  make ollama-qwen       Run qwen3.5:9b' \
	  '  make webui            Start only Open WebUI in Docker' \
	  '  make webui-up         Alias for make webui' \
	  '  make webui-down       Stop WebUI, keeping its data volume' \
	  '  make webui-logs       Follow WebUI logs' \
	  '  make webui-ps         Show WebUI status' \
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

ollama-serve:
	OLLAMA_HOST="$(OLLAMA_BIND)" $(OLLAMA) serve

ollama:
	$(OLLAMA) run "$(MODEL)"

ollama-gemma:
	$(OLLAMA) run "gemma4:e4b-it-q4_K_M"

ollama-qwen:
	$(OLLAMA) run "qwen3.5:9b"

webui: webui-up

webui-up: check-env
	$(WEBUI_COMPOSE) up -d open-webui

webui-down: check-env
	$(WEBUI_COMPOSE) down

webui-logs: check-env
	$(WEBUI_COMPOSE) logs -f open-webui

webui-ps: check-env
	$(WEBUI_COMPOSE) ps
