pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
        }

    environment {
        BASE_TAG = "${BUILD_NUMBER}"
        }

    stages {
        stage('Checkout') {
            steps {
                echo '[INFO] Checkout del repositorio y limpieza de workspace...'
                deleteDir()
                checkout scm
                sh 'rm -rf .scannerwork || true'
                echo '[OK] Código listo.'
            }
        }

        stage('Load ci.properties') {
            steps {
                script {
                    if (!fileExists('ci.properties')) {
                    error "[ERROR] No se encontró el archivo ci.properties en la raíz del proyecto."
                    }
                def props = readProperties file: 'ci.properties'

                env.APP_NAME        = props.APP_NAME
                env.APP_DIR         = props.APP_DIR
                env.CONTAINER_NAME  = props.CONTAINER_NAME

                env.DOCKER_NET      = props.DOCKER_NET
                env.PUSH_REGISTRY   = props.PUSH_REGISTRY

                env.APP_PORT        = props.APP_PORT
                env.HOST_PORT       = props.HOST_PORT

                env.SONAR_HOST_URL  = props.SONAR_HOST_URL
                env.SONAR_PROJECT_KEY = props.SONAR_PROJECT_KEY

                env.SKIP_TESTS      = props.SKIP_TESTS
                }

                sh '''
                echo "[INFO] Config cargada:"
                echo " - APP_NAME=$APP_NAME"
                echo " - PUSH_REGISTRY=$PUSH_REGISTRY"
                echo " - SONAR_PROJECT_KEY=$SONAR_PROJECT_KEY"
                echo " - HOST_PORT=$HOST_PORT -> APP_PORT=$APP_PORT"
                echo " - SKIP_TESTS=$SKIP_TESTS"
                '''
            }
        }

        stage('Build (Maven)') {
            steps {
                sh '''
                set -e
                echo "[INFO] Permisos de ejecución para mvnw..."
                chmod +x ./mvnw || true

                echo "[INFO] Build Maven..."
                if [ "$SKIP_TESTS" = "true" ]; then
                    ./mvnw -B -DskipTests clean package
                else
                    ./mvnw -B clean package
                fi
                echo "[OK] Build Maven completado."
                ls -lah target || true
                '''
            }
        }

        stage('Read Version (pom.xml)') {
            steps {
                sh '''
                set -e
                chmod +x ./mvnw || true

                echo "[INFO] Leyendo version desde pom.xml..."
                VERSION=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version | tr -d '\\r' | tail -n 1)

                if [ -z "$VERSION" ]; then
                    echo "[ERROR] No se pudo obtener la versión del pom.xml"
                    exit 1
                fi

                echo "$VERSION" > .app_version
                echo "[OK] Version detectada: $VERSION"
                '''
                script {
                env.APP_VERSION = readFile('.app_version').trim()
                }
            }
        }


        stage('SonarQube Scan') {
            environment {
                SONAR_TOKEN = credentials('sonar-token')
            }
            steps {
                sh '''
                set -e
                chmod +x mvnw || true
                ./mvnw -B -DskipTests clean package

                echo "[INFO] Preparando carpeta .scannerwork..."
                rm -rf .scannerwork || true
                mkdir -p .scannerwork
                chmod -R 777 .scannerwork || true

                JENKINS_CID="$(hostname)"

                docker run --rm \
                    --user 0:0 \
                    --network "$DOCKER_NET" \
                    --volumes-from "$JENKINS_CID" \
                    -w /var/jenkins_home/jobs/firma_digital/workspace \
                    sonarsource/sonar-scanner-cli:latest \
                    sonar-scanner \
                    -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
                    -Dsonar.sources=src \
                    -Dsonar.java.binaries=target/classes \
                    -Dsonar.host.url="$SONAR_HOST_URL" \
                    -Dsonar.login="$SONAR_TOKEN" \
                    -Dsonar.working.directory=".scannerwork"

                test -f .scannerwork/report-task.txt
                echo "[OK] Scan enviado a SonarQube."
                '''
            }
        }


        stage('Quality Gate Result') {
            environment {
                SONAR_TOKEN = credentials('sonar-token')
            }
            steps {
                sh '''
                set -e

                info(){ echo "[INFO] $*"; }
                ok(){  echo "[OK]   $*"; }
                warn(){ echo "[WARN] $*"; }

                REPORT=".scannerwork/report-task.txt"
                if [ ! -f "$REPORT" ]; then
                    warn "No existe $REPORT. Se marca como qg-f."
                    echo "qg-f" > .qg_tag
                    exit 0
                fi

                CE_TASK_URL=$(grep -E '^ceTaskUrl=' "$REPORT" | cut -d= -f2-)
                info "Esperando procesamiento del reporte en Sonar..."
                ANALYSIS_ID=""
                STATUS=""
                for i in $(seq 1 120); do
                    JSON=$(curl -s -u "$SONAR_TOKEN:" "$CE_TASK_URL")
                    STATUS=$(echo "$JSON" | sed -n 's/.*"status":"\\([^"]*\\)".*/\\1/p' | head -n1)

                    if [ "$STATUS" = "SUCCESS" ]; then
                    ANALYSIS_ID=$(echo "$JSON" | sed -n 's/.*"analysisId":"\\([^"]*\\)".*/\\1/p' | head -n1)
                    break
                    fi

                    if [ "$STATUS" = "FAILED" ] || [ "$STATUS" = "CANCELED" ]; then
                    warn "Compute Engine terminó con status=$STATUS => qg-f"
                    echo "qg-f" > .qg_tag
                    exit 0
                    fi

                    sleep 2
                done

                if [ -z "$ANALYSIS_ID" ]; then
                    warn "Timeout esperando analysisId => qg-f"
                    echo "qg-f" > .qg_tag
                    exit 0
                fi

                QG_URL="$SONAR_HOST_URL/api/qualitygates/project_status?analysisId=$ANALYSIS_ID"
                QG_JSON=$(curl -s -u "$SONAR_TOKEN:" "$QG_URL")
                QG_STATUS=$(echo "$QG_JSON" | sed -n 's/.*"status":"\\([^"]*\\)".*/\\1/p' | head -n1)

                info "Quality Gate: $QG_STATUS"

                if [ "$QG_STATUS" = "OK" ]; then
                    ok "Quality Gate PASSED => qg-p"
                    echo "qg-p" > .qg_tag
                else
                    warn "Quality Gate FAILED => qg-f"
                    echo "qg-f" > .qg_tag
                fi
                '''
            }
        }



        stage('Docker Build Image') {
            steps {
                sh '''
                set -e

                IMAGE="${PUSH_REGISTRY}/${APP_NAME}:${APP_VERSION}-dev-${BASE_TAG}"

                echo "[INFO] Construyendo imagen final..."
                echo "[INFO] Tag final => $IMAGE"

                docker build --quiet -t "$IMAGE" "$APP_DIR" >/dev/null 2>&1 || docker build -t "$IMAGE" "$APP_DIR"

                echo "$IMAGE" > .image_name
                echo "[OK] Imagen construida."
                '''
            }
        }

        stage('Push to Nexus (app image)') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-docker', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                sh '''
                    set -e
                    IMAGE=$(cat .image_name)

                    echo "[INFO] Subiendo imagen a Nexus..."
                    echo "$NEXUS_PASS" | docker login "$PUSH_REGISTRY" -u "$NEXUS_USER" --password-stdin >/dev/null 2>&1 || true
                    docker push "$IMAGE" >/dev/null 2>&1 || docker push "$IMAGE"
                    echo "[OK] Imagen subida => $IMAGE"
                '''
                }
            }
        } 

        stage('Deploy docker') {
            steps {
                sh '''
                set -e
                IMAGE=$(cat .image_name)

                echo "[INFO] Deploy..."
                echo "[INFO] Contenedor => $CONTAINER_NAME"
                echo "[INFO] Imagen     => $IMAGE"

                docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
                docker rm   "$CONTAINER_NAME" >/dev/null 2>&1 || true

                docker run -d --name "$CONTAINER_NAME" \
                    --network "$DOCKER_NET" \
                    -p ${HOST_PORT}:${APP_PORT} \
                    "$IMAGE" >/dev/null

                echo "[OK] Deploy completado. URL: http://localhost:${HOST_PORT}"
                docker ps --filter "name=$CONTAINER_NAME"
                '''
            }
        }

  triggers {
    GenericTrigger(
      genericVariables: [
        [key: 'GIT_REF', value: '$.ref'],
        [key: 'REPO_NAME', value: '$.repository.name'],
        [key: 'REPO_URL', value: '$.repository.clone_url'],
        [key: 'PUSHER', value: '$.pusher.name'],
        [key: 'COMMIT_ID', value: '$.head_commit.id'],
        [key: 'COMMIT_MSG', value: '$.head_commit.message']
      ],
      causeString: 'Push by $PUSHER on $REPO_NAME',
      token: 'mi-token-secreto',
      printContributedVariables: true,
      printPostContent: true
    )
  }

  stages {
    stage('Debug variables') {
      steps {
        sh '''
          echo "Branch: $GIT_REF"
          echo "Repo: $REPO_NAME"
          echo "URL: $REPO_URL"
          echo "Pusher: $PUSHER"
          echo "Commit: $COMMIT_ID"
          echo "Mensaje: $COMMIT_MSG"
        '''
      }
    }
  }
}


  }
}
