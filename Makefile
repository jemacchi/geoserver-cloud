all: install test build-image

TAG=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
DOCKERHUB_REPO := $(DOCKER_HUB_USERNAME)
COSIGN_PASSWORD := $(COSIGN_PASSWORD)

clean:
	./mvnw clean

lint:
	./mvnw fmt:check sortpom:verify -Dsort.verifyFailOn=strict -Dsort.verifyFail=stop -ntp

format:
	./mvnw sortpom:sort fmt:format -ntp

install:
	./mvnw clean install -DskipTests -ntp -T4 -U

test:
	./mvnw verify -ntp -T4

build-base-images:
	./mvnw clean package -f src/apps/base-images -DksipTests -T4 && \
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	DOCKERHUB_REPO=$(DOCKERHUB_REPO) \
	docker compose -f docker-build/base-images.yml build 

build-image-infrastructure:
	./mvnw clean package -f src/apps/infrastructure -DskipTests -T4 && \
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	DOCKERHUB_REPO=$(DOCKERHUB_REPO) \
	docker compose -f docker-build/infrastructure.yml build

build-image-geoserver:
	./mvnw clean package -f src/apps/geoserver -DskipTests -T4 && \
	COMPOSE_DOCKER_CLI_BUILD=1 \
	DOCKER_BUILDKIT=1 \
	TAG=$(TAG) \
	DOCKERHUB_REPO=$(DOCKERHUB_REPO) \
	docker compose -f docker-build/geoserver.yml build 
  
build-image: build-base-images build-image-infrastructure build-image-geoserver

push-image:
	TAG=$(TAG) \
	DOCKERHUB_REPO=$(DOCKERHUB_REPO) \
	docker compose \
	-f docker-build/infrastructure.yml \
	-f docker-build/geoserver.yml \
	push

.PHONY: sign-image
sign-image:
	@bash -c '\
	export COSIGN_PASSWORD=$(COSIGN_PASSWORD); \
	images=$$(docker images --format "{{.Repository}}@{{.Digest}}" | grep "geoserver-cloud-"); \
	for image in $$images; do \
	  echo "Signing $$image"; \
	  output=$$(cosign sign --yes --key cosign.key $$image 2>&1); \
	  if [ $$? -ne 0 ]; then \
	    echo "Error occurred: $$output"; \
	    exit 1; \
	  else \
	    echo "Signing successful: $$output"; \
	  fi; \
	done'

.PHONY: verify-image
verify-image:
	@bash -c '\
	images=$$(docker images --format "{{.Repository}}@{{.Digest}}" | grep "geoserver-cloud-"); \
	for image in $$images; do \
	  echo "Verifying $$image"; \
	  output=$$(cosign verify --key cosign.pub $$image 2>&1); \
	  if [ $$? -ne 0 ]; then \
	    echo "Error occurred: $$output"; \
	    exit 1; \
	  else \
	    echo "Verification successful: $$output"; \
	  fi; \
	done'

