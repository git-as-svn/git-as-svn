pipeline {
    agent {
        dockerfile {
            filename 'Dockerfile.build'
            reuseNode false
        }
    }

    stages {
        stage('Cleanup') {
            steps {
                sh """
git clean -fdx
"""
            }
        }
        stage('Build') {
            steps {
                sh """
./gradlew assembleDist
"""
            }
        }
        stage('Test') {
            steps {
                realtimeJUnit ("build/test-results/test/*.xml") {
                    sh """
./gradlew check -PtestIgnoreFailures=true
"""
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: "build/distributions/*", fingerprint: true
        }
    }
}
