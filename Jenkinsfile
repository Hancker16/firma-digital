pipeline {
  agent any

  options {
    skipDefaultCheckout(true)
    timestamps()
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

    stage('SonarQube Scan (Maven)') {
      environment {
        SONAR_TOKEN = credentials('sonar-token')
      }
      steps {
        sh '''
          set -e
          echo "[INFO] Ejecutando análisis SonarQube con Maven..."
          ./mvnw -B sonar:sonar \
            -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
            -Dsonar.host.url="$SONAR_HOST_URL" \
            -Dsonar.login="$SONAR_TOKEN"
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
            warn "No existe $REPORT. Se marca como qg-f (fallo/indeterminado)."
            echo "qg-f" > .qg_tag
            exit 0
          fi

          CE_TASK_URL=$(grep -E '^ceTaskUrl=' "$REPORT" | cut -d= -f2-)
          info "Esperando resultado del análisis en Sonar (Compute Engine)..."

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

          info "analysisId obtenido: $ANALYSIS_ID"
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
          QG_TAG=$(cat .qg_tag)
          IMAGE="${PUSH_REGISTRY}/${APP_NAME}:${BASE_TAG}-${QG_TAG}"

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
  }
}
