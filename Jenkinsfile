pipeline {
    agent any

    environment {
        SONAR_URL = 'http://sonarqube:9000'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // ── 백엔드 빌드 ──
        stage('Backend - Build') {
            steps {
                dir('back-pressure-practice') {
                    sh 'chmod +x gradlew'
                    sh './gradlew clean build -x test'
                }
            }
        }

        // ── 백엔드 테스트 + 커버리지 ──
        stage('Backend - Test') {
            steps {
                dir('back-pressure-practice') {
                    sh './gradlew test jacocoTestReport'
                }
            }
        }

        // ── 백엔드 SonarQube 분석 ──
        stage('Backend - SonarQube Analysis') {
            steps {
                dir('back-pressure-practice') {
                    withSonarQubeEnv('SonarQube') {
                        sh './gradlew sonarqube'
                    }
                }
            }
        }

        // ── 프론트엔드 SonarQube 분석 ──
        stage('Frontend - SonarQube Analysis') {
            steps {
                dir('robot-dashboard') {
                    withSonarQubeEnv('SonarQube') {
                        sh 'sonar-scanner'
                    }
                }
            }
        }

        // ── Quality Gate 체크 (실패 시 빌드 중단) ──
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ── 이후 단계는 연주(파이프라인), 강산(블루/그린)이 추가 ──
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
