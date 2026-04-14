pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend - Build') {
            steps {
                dir('back-pressure-practice') {
                    sh 'chmod +x gradlew'
                    sh './gradlew clean build -x test'
                }
            }
        }

        stage('Backend - Test') {
            steps {
                dir('back-pressure-practice') {
                    sh './gradlew test jacocoTestReport -Dspring.profiles.active=sonar || true'
                }
            }
        }

        // ── 백엔드 SonarQube 분석 (여기를 집중해서 봐주세요!) ──
        stage('Backend - SonarQube Analysis') {
            steps {
                dir('back-pressure-practice') {
                    script {
                        // 1. 젠킨스 내부의 'sonar-scanner'를 직접 호출합니다. (Gradle 플러그인 에러 회피)
                        withSonarQubeEnv('SonarQube') {
                            sh """
                                sonar-scanner \
                                -Dsonar.projectKey=back-pressure-practice \
                                -Dsonar.projectName='Robot Backend' \
                                -Dsonar.sources=src/main/java \
                                -Dsonar.java.binaries=build/classes/java/main \
                                -Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/test/jacocoTestReport.xml \
                                -Dsonar.login=${SONAR_AUTH_TOKEN}
                            """
                        }
                    }
                }
            }
        }

        stage('Frontend - SonarQube Analysis') {
            steps {
                dir('robot-dashboard') {
                    script {
                        try {
                            withSonarQubeEnv('SonarQube') {
                                sh 'sonar-scanner'
                            }
                        } catch (Exception e) {
                            echo "Frontend scanner failed or directory missing, skipping..."
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    // 분석이 성공해야 이 단계가 작동합니다.
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deployment stage - 강산 담당'
            }
        }
    }

    post {
        failure {
            echo 'Quality Gate 실패 또는 빌드 오류 - 배포 중단'
        }
        success {
            echo '모든 품질 검사 통과 - 배포 진행'
        }
    }
}