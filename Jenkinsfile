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

    stage('SonarQube Scan') {
        environment {
            SONAR_TOKEN = credentials('sonar-token')
            }
            steps {
                sh '''
                set -e
                echo "[INFO] SonarQube Scan (CLI)..."
                chmod +x mvnw || true
                ./mvnw -B -DskipTests clean package

                # Descarga sonar-scanner (si no lo tienes instalado)
                if ! command -v sonar-scanner >/dev/null 2>&1; then
                    echo "[INFO] Instalando sonar-scanner..."
                    curl -sSLo /tmp/sonar-scanner.zip https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-5.0.1.3006-linux.zip
                    unzip -q /tmp/sonar-scanner.zip -d /tmp
                    export PATH="/tmp/sonar-scanner-5.0.1.3006-linux/bin:$PATH"
                fi

                sonar-scanner \
                    -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
                    -Dsonar.sources=src \
                    -Dsonar.java.binaries=target/classes \
                    -Dsonar.host.url="$SONAR_HOST_URL" \
                    -Dsonar.token="$SONAR_TOKEN"
                '''
                }
    }

    stage('Quality Gate') {
    steps {
        timeout(time: 10, unit: 'MINUTES') {
        script {
            def qg = waitForQualityGate()
            echo "Quality Gate status: ${qg.status}"
            if (qg.status != 'OK') {
            echo "qg-f" > .qg_tag
            } else {
            echo "qg-p" > .qg_tag
            }
        }
        }
    }
    }
+
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

