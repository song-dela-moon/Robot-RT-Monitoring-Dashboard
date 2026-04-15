pipeline {
    agent any

    options {
        // Keep only the last 10 builds to save disk space
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        // --- Image Names (local only, no registry) ---
        BE_IMAGE = "robot-backend"
        FE_IMAGE = "robot-frontend"
        TAG      = "${BUILD_NUMBER}"

        // --- DB Password (Jenkins Secret) ---
        DB_PASSWORD_CRED = "robot-db-password"

        // --- Deploy paths (same server as Jenkins) ---
        DEPLOY_BASE_DIR = "/opt/robot_deploy"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Static Analysis (SonarQube)') {
            steps {
                dir('back-pressure-practice') {
                    echo "🚀 테스트 환경을 생략하고 소나큐브로 코드를 전송하여 정적 분석을 진행합니다..."
                    
                    // 권한 부여 및 빌드 (테스트 제외) - SonarQube 분석을 위해서는 클래스 파일이 필요합니다.
                    sh 'chmod +x gradlew'
                    sh './gradlew clean build -x test'
                    
                    // 젠킨스 시스템 설정에 등록된 'SonarQube' 서버 토큰을 자동으로 가져와 전송
                    withSonarQubeEnv('SonarQube') { 
                        sh '''
                            ./gradlew sonar \
                            -Dsonar.projectKey=sw_team_3_robot_backend \
                            -Dsonar.projectName="Team 3 Robot Backend" \
                            -Dsonar.java.binaries=build/classes
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo "🚀 소나큐브의 합격(Pass)/불합격(Fail) 판정을 기다립니다..."
                
                // 최대 1분 대기, 기준 미달 시 파이프라인 즉시 중단
                timeout(time: 1, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            parallel {
                stage('Backend Build') {
                    steps {
                        echo "🚀 Starting Backend Docker Build..."
                        sh "docker build -t ${BE_IMAGE}:${TAG} -t ${BE_IMAGE}:latest ./back-pressure-practice"
                    }
                }
                stage('Frontend Build') {
                    steps {
                        echo "🚀 Starting Frontend Docker Build..."
                        sh "docker build -t ${FE_IMAGE}:${TAG} -t ${FE_IMAGE}:latest ./robot-dashboard"
                    }
                }
            }
        }

        stage('Blue-Green Deploy') {
            steps {
                script {
                    withCredentials([
                        string(
                            credentialsId: "${DB_PASSWORD_CRED}",
                            variable: 'DB_PASSWORD'
                        )
                    ]) {
                        sh """
                            # Ensure docker-compose binary exists
                            mkdir -p \$HOME/bin
                            if ! command -v docker-compose &> /dev/null; then
                                echo "🚀 Installing docker-compose binary..."
                                curl -L "https://github.com/docker/compose/releases/download/v2.24.5/docker-compose-linux-x86_64" -o \$HOME/bin/docker-compose
                                chmod +x \$HOME/bin/docker-compose
                            fi
                            
                            export PATH=\$HOME/bin:\$PATH
                            export BE_IMAGE=${BE_IMAGE}:${TAG}
                            export FE_IMAGE=${FE_IMAGE}:${TAG}
                            export DB_PASSWORD=\$DB_PASSWORD
                            export DEPLOY_BASE_DIR=${DEPLOY_BASE_DIR}
                            
                            bash ./scripts/deploy.sh
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "✅ [Build #${TAG}] successfully deployed!"
        }
        failure {
            echo "❌ [Build #${TAG}] deployment failed. Check logs above."
        }
        always {
            // Clean up old images (keep last 3)
            sh """
                docker images ${BE_IMAGE} --format '{{.Tag}}' | sort -n | head -n -3 | xargs -r -I{} docker rmi ${BE_IMAGE}:{} || true
                docker images ${FE_IMAGE} --format '{{.Tag}}' | sort -n | head -n -3 | xargs -r -I{} docker rmi ${FE_IMAGE}:{} || true
            """
        }
    }
}
